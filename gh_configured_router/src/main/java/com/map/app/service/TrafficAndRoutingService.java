package com.map.app.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.PtRouterImpl;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.UnsignedDecimalEncodedValue;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.BBox;
import com.map.app.containers.AirQualityDataExtractor;
import com.map.app.containers.RoutePathContainer;
import com.map.app.containers.TrafficDataExtractor;
import com.map.app.graphhopperfuncs.MyGraphHopper;
import com.map.app.model.RoutePath;
import com.map.app.model.TrafficData;
import com.map.app.model.UrlContainer;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * @author Siftee, Amit
 */

@Service
public class TrafficAndRoutingService {

	private String apiKey = System.getenv("here_api_key");
	
	private final AirQualityDataExtractor ai;
	private final TrafficDataExtractor dt;
	private final RoutePathContainer rp;
	private final BBox boundingBox;
	public static PtRouter ptRouter;

	// a few settings for here maps real-time congestion data
	public enum SpeedChoice{avg_actual_from_hereMaps, free_flow_from_hereMaps, lower_of_two}
	public static SpeedChoice speedChoice = SpeedChoice.avg_actual_from_hereMaps;
	public static float functional_road_class_here_maps = 4.0f;

	public TrafficAndRoutingService() {
		ReadWriteLock lock=new ReentrantReadWriteLock();
    	GraphHopperConfig config=new GraphHopperConfig();
    	config.putObject("index.max_region_search", 8); // increasing the search radius (a point in Rajaji forest is not able to find any road)
    	UnsignedDecimalEncodedValue smokeEnc=new UnsignedDecimalEncodedValue("smoke",16,0.1,0,true); //maxValue->155.5

		GraphHopper gh=new MyGraphHopper();
    	//gh.getEncodingManager().
    	//gh.createWeighting(null, null, false)
    	gh.getEncodingManagerBuilder().add(smokeEnc);
    	//gh.c
    	Properties prop=new Properties();
		try(FileInputStream ip = new FileInputStream("config.properties")) {
			prop.load(ip);
			System.out.println("Using OSM file "+ prop.getProperty("datareader.file"));
			config.putObject("datareader.file",prop.getProperty("datareader.file"));
			List<Profile> profiles = new ArrayList<>();

			for (PathChoice pc : PathChoice.values()) {
				for (TransportMode tm : TransportMode.values()) {
					if(pc.toString().equals("all")==false)
						profiles.add(new Profile(TrafficAndRoutingService.getModeBasedPathChoice(pc, tm)).setVehicle(tm.toString()).setWeighting(pc.toString()));
				}
			}

			profiles.add(new Profile("ipt").setVehicle("car").setWeighting("fastest"));

			// see https://github.com/graphhopper/graphhopper/blob/4.x/docs/core/custom-models.md
			CustomModel bus_custom_model = new CustomModel();
			bus_custom_model.addToPriority(Statement.If( "road_class == RESIDENTIAL", Statement.Op.LIMIT, 0.1));
			profiles.add(new CustomProfile("bus").setCustomModel(bus_custom_model).setVehicle("car"));

			CustomModel metro_custom_model = new CustomModel();
			metro_custom_model.addToPriority(Statement.If( "road_class != TRUNK", Statement.Op.LIMIT, 0.1));
			profiles.add(new CustomProfile("metro").setCustomModel(metro_custom_model).setVehicle("car"));

			config.setProfiles(profiles);
			config.putObject("graph.flag_encoders",prop.getProperty("graph.flag_encoders"));
			config.putObject("graph.dataaccess", prop.getProperty("graph.dataaccess"));
			GraphHopperConfig ghGtfsConfig = new GraphHopperConfig();
			ghGtfsConfig.putObject("graph.location", "target/gtfs");
			ghGtfsConfig.putObject("gtfs.file", "D:\\gh2\\graphhopper\\gtfs-vbb.zip");
			ghGtfsConfig.setProfiles(Arrays.asList(
					new Profile("foot").setVehicle("foot").setWeighting("fastest"),
					new Profile("car").setVehicle("car").setWeighting("fastest")));
			Helper.removeDir(new File("target/gtfs"));
			GraphHopperGtfs graphHopperGtfs = new GraphHopperGtfs(ghGtfsConfig);
			graphHopperGtfs.init(ghGtfsConfig);
			graphHopperGtfs.importOrLoad();
			ptRouter = PtRouterImpl.createFactory(ghGtfsConfig, new TranslationMap().doImport(), graphHopperGtfs, graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage()).createWithoutRealtimeFeed();
			if( apiKey==null) apiKey =prop.getProperty("here_api_key"); // the api key must be in either system env or config.properties
		} catch (IOException e) {
			throw new RuntimeException("Config properties are not found. Aborting ...");
		}

    	gh.init(config).setGraphHopperLocation("graphLocation");
    	//System.out.println(gh.getEncodingManager().getDecimalEncodedValue("smoke"));
    	gh.clean();
    	gh.importOrLoad();
    	this.boundingBox = gh.getGraphHopperStorage().getBaseGraph().getBounds();
    	dt=new TrafficDataExtractor(gh,lock.writeLock());
    	rp=new RoutePathContainer(gh, lock.readLock());
    	ai=new AirQualityDataExtractor(gh,lock.writeLock());
    }
	
	public static String getModeBasedPathChoice(PathChoice pathChoice, TransportMode transportMode) {
		return pathChoice.toString().concat("_").concat(transportMode.toString());
	}
	
	public ArrayList<Float> getBoundingBox() {
		ArrayList<Float> box=new ArrayList<>();
		box.add((float)boundingBox.minLat);
		box.add((float)boundingBox.minLon);
		box.add((float) boundingBox.maxLat);
		box.add((float)boundingBox.maxLon);
		return box;
	}
    public TrafficData getAll()
	{
		return dt.getRoads();
	}
	
	public void start()
	{
		if (apiKey.equals("<HERE_API_KEY>")){
			throw new RuntimeException("API Key for Here Maps is not found. Aborting...");
		}
	    dt.fetchData(apiKey, this.boundingBox);
		ai.readJSON(this.boundingBox);
	}

	public ArrayList<RoutePath> getPath(UrlContainer p)
	{
		return rp.find(p);
	}

}

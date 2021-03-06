package com.map.app.controller;

import com.map.app.containers.UrlTransformer;
import com.map.app.model.RoutePath;
import com.map.app.model.TrafficData;
import com.map.app.model.UrlContainer;
import com.map.app.service.TrafficAndRoutingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

/**
 * @author Siftee
 */
@Controller
public class MapControl {
	@Autowired
	TrafficAndRoutingService trs;
	
	
	
	@GetMapping(value="/")
	public String read(Model model)
	{
		model.addAttribute("pt",new UrlTransformer());
		model.addAttribute("bbox",trs.getBoundingBox());
		return "index";
	}
    
	@RequestMapping(value="/routing",method=RequestMethod.GET)
	public String load(@ModelAttribute("pt") UrlTransformer pt, BindingResult errors, Model model)
    {
		
    UrlContainer rp=pt.convert();
//    System.out.println(rp.toString());
	ArrayList<RoutePath> res=trs.getPath(rp);
	model.addAttribute("route",res);
	model.addAttribute("bbox",res.get(0).getBounds());
   	return "index";
	}
	
	@ResponseBody 
	@RequestMapping(value = "/routing",method=RequestMethod.GET,produces="application/json")   
	 public ArrayList<RoutePath> fetchJSONResponse(@ModelAttribute("pt") UrlTransformer pt, BindingResult errors, Model model)
	 {
		 UrlContainer rp=pt.convert();
		 return trs.getPath(rp);
	 }
	
    @RequestMapping(value = "/traffic", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public TrafficData show()
    {
    return trs.getAll();	
    }
    
   
    
 
}

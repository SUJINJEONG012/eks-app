package actions.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActionsController {
	
	@GetMapping(value = "/api/actions")
	public String actionsTest() {
		return "app-with-actions version:0.0.1";
	}
	
	@GetMapping(value = "/api/status")
	public String statusTest() {
		return "Status - returned by Pod";
	}

	@GetMapping(value= "/api/status2")
	public String statusTest1(){	
		return "Statsu - returned by Pod2";
	}
	@GetMapping(value= "/api/status3")
	public String statusTest2(){	
		return "Statsu - returned by Pod3";
	}
	

}

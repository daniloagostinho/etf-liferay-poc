package com.example.etf.simulator.internal.jaxrs.application;

import java.util.Collections;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.osgi.service.component.annotations.Component;

@Component(
	property = {
		"auth.verifier.guest.allowed=true",
		"liferay.access.control.disable=true",
		"liferay.jackson=false",
		"osgi.jaxrs.application.base=/etf-simulator",
		"osgi.jaxrs.extension.select=(osgi.jaxrs.name=Liferay.Vulcan)",
		"osgi.jaxrs.name=EtfSimulator.Rest"
	},
	service = Application.class
)
public class EtfSimulatorApplication extends Application {

	@Override
	public Set<Object> getSingletons() {
		return Collections.emptySet();
	}

}
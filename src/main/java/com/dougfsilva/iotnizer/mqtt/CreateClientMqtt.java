package com.dougfsilva.iotnizer.mqtt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.springframework.stereotype.Service;

import com.dougfsilva.iotnizer.model.User;

@Service
public class CreateClientMqtt {

	private final MqttParams mqtt;
	
	public CreateClientMqtt(MqttParams mqtt) {
		this.mqtt = mqtt;
	}

	public void create(User user) {
		String username = mqtt.getDefaultClientUsername(user);
		String rolename = mqtt.getDefaultRolename(user.getId());
		try {
			Process createClient = Runtime.getRuntime().exec(mqtt.getDynSecUriCommand() + String.format("createClient %s", username));
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(createClient.getOutputStream()));
			out.write(user.getClientMqttPassword() + "\n");
			out.flush();
			out.write(user.getClientMqttPassword() + "\n");
			out.flush();
			out.close();
			Runtime.getRuntime().exec(mqtt.getDynSecUriCommand() + String.format("createRole %s", rolename));
			Runtime.getRuntime().exec(mqtt.getDynSecUriCommand() + String.format("addClientRole %s %s", username, rolename));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

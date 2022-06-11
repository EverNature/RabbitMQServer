package server;

import java.io.ByteArrayInputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RESTClient {
	
	private RESTClient() {
		
	}
	
	public static Result sendPhotos(byte[] photo) {
		String urlRestService = "https://ia.evern.eus/predict";
		Client client = ClientBuilder.newBuilder()
			    .register(MultiPartFeature.class)
			    .build();
		StreamDataBodyPart formPart = new StreamDataBodyPart("file", new ByteArrayInputStream(photo));
		MultiPart multipartEntity = new MultiPart();
		multipartEntity.bodyPart(formPart);
		
		WebTarget target = client.target(urlRestService);
		Response response = target.request().post(Entity.entity(multipartEntity, MediaType.MULTIPART_FORM_DATA_TYPE));
		Result result;
		if (response.getStatus() == 200) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			result = gson.fromJson(response.readEntity(String.class), Result.class);
		} else {
			result = null;
		}
		return result;
	}
	
	public static boolean sendToNodeTelegram(NodeClass nc) {
		String urlRestService = "http://192.168.1.10:1880/EnviarTelegram";
		Client client = ClientBuilder.newBuilder().build();
		WebTarget target = client.target(urlRestService);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Response response = target.request().post(Entity.json(gson.toJson(nc)));
		Boolean toReturn;
		if (response.getStatus() == 200) {
			toReturn = true;
		} else {
			toReturn = false;
		}
		return toReturn;
	}
	
	public static boolean sendToNodeMail(NodeClass nc) {
		String urlRestService = "http://192.168.1.10:1880/EnviarCorreo";
		Client client = ClientBuilder.newBuilder().build();
		WebTarget target = client.target(urlRestService);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Response response = target.request().post(Entity.json(gson.toJson(nc)));
		Boolean toReturn;
		if (response.getStatus() == 200) {
			toReturn = true;
		} else {
			toReturn = false;
		}
		return toReturn;
	}

	public static boolean sendToNodeDataBase(RecordDTO rDto) {
		String urlRestService = "http://192.168.1.10:1880/GuardarDatos";
		Client client = ClientBuilder.newBuilder().build();
		WebTarget target = client.target(urlRestService);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Response response = target.request().post(Entity.json(gson.toJson(rDto)));
		Boolean toReturn;
		if (response.getStatus() == 200) {
			toReturn = true;
		} else {
			toReturn = false;
		}
		return toReturn;
	}
	
	public static boolean checkIfInvasive(AnimalIsInvasor aii) {
		String urlRestService = "http://192.168.1.10:1880/esInvasor";
		Client client = ClientBuilder.newBuilder().build();
		WebTarget target = client.target(urlRestService);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Response response = target.request().post(Entity.json(gson.toJson(aii)));
		Boolean toReturn;
		if (response.getStatus() == 200) {
			String output = response.readEntity(String.class);
			AnimalIsInvasor aii2 = gson.fromJson(output, AnimalIsInvasor.class);
			toReturn = aii2.isInvasor();
		} else {
			toReturn = false;
		}
		return toReturn;
	}
}
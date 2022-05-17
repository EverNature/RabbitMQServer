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
	String urlRestService = "http://localhost:8080/predict";
	Client client;
	
	public RESTClient() {
		client = ClientBuilder.newBuilder()
			    .register(MultiPartFeature.class)
			    .build();
	}
	
	public String sendPhotos(byte[] photo) {
		StreamDataBodyPart formPart = new StreamDataBodyPart("file", new ByteArrayInputStream(photo));
		MultiPart multipartEntity = new MultiPart();
		multipartEntity.bodyPart(formPart);
		
		WebTarget target = client.target(urlRestService);
		Response response = target.request().post(Entity.entity(multipartEntity, MediaType.MULTIPART_FORM_DATA_TYPE));
		String res;
		if (response.getStatus() == 200) {
			Result result;
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			result = gson.fromJson(response.readEntity(String.class), Result.class);
			res = result.getPrediction().get(0).getClase();
		} else {
			res = "La llamada no ha sido correcta";
		}
		return res;
	}
}
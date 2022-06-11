package server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;

import javax.ws.rs.ProcessingException;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JSONValidation {
	
	private JSONValidation() {
		super();
	}
	
	public static void isJsonValid(Result result) throws ProcessingException, IOException, ValidationException {
		
		File schema = new File(FileSystems.getDefault().getPath("resources", "json-schema", "schema.json").toString());

		JSONTokener tokener = new JSONTokener(new FileInputStream(schema));
		JSONObject jsonSchema = new JSONObject(tokener);

		// json data
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		InputStream targetStream = new ByteArrayInputStream(gson.toJson(result).getBytes());
		JSONTokener jsonDataTokener = new JSONTokener(targetStream);
        JSONObject jsonDataJson = new JSONObject(jsonDataTokener);

		// validate schema
		Schema schemaJson = SchemaLoader.load(jsonSchema);
		schemaJson.validate(jsonDataJson);
	}
}

package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;

import javax.ws.rs.ProcessingException;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.simple.JSONValue;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONValidation {
	public static void isJsonValid(Result result) throws ProcessingException, IOException, ValidationException {
		
		File schema = new File(FileSystems.getDefault().getPath("json-schema", "schema.json").toString());

		JSONTokener tokener = new JSONTokener(new FileInputStream(schema));
		JSONObject josnSchema = new JSONObject(tokener);

		// json data
		JSONObject jsonDataJson = (JSONObject) JSONValue.parse(new ObjectMapper().writeValueAsString(result));

		// validate schema
		Schema schemaJson = SchemaLoader.load(josnSchema);
		schemaJson.validate(jsonDataJson);
	}
}

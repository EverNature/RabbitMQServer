package server;

import com.google.gson.annotations.SerializedName;

public class Prediction {
	@SerializedName(value = "class")
	String clase;
	String confidence;
	Boolean predicted;
	String msg;
	String image;
	
	public Prediction() {
		super();
	}

	public String getClase() {
		return clase;
	}

	public void setClase(String clase) {
		this.clase = clase;
	}

	public String getConfidence() {
		return confidence;
	}

	public void setConfidence(String confidence) {
		this.confidence = confidence;
	}

	public Boolean getPredicted() {
		return predicted;
	}

	public void setPredicted(Boolean predicted) {
		this.predicted = predicted;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}
}

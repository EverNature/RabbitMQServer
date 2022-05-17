package server;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class Result {
	List<Prediction> prediction;
	Boolean segmented;
	String date;
	
	@SerializedName(value = "full_image")
	String fullImage;
	
	public Result() {
	}

	public List<Prediction> getPrediction() {
		return prediction;
	}

	public void setPrediction(List<Prediction> prediction) {
		this.prediction = prediction;
	}

	public Boolean getSegmented() {
		return segmented;
	}

	public void setSegmented(Boolean segmented) {
		this.segmented = segmented;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public String getFullImage() {
		return fullImage;
	}

	public void setFullImage(String fullImage) {
		this.fullImage = fullImage;
	}
}

package server;

import java.util.ArrayList;
import java.util.List;

public class RecordDTO {
	
	List<PredictionDTO> predictions = new ArrayList<>();
    String image;
    private Integer cameraId;
    
	public RecordDTO() {
		predictions = new ArrayList<>();
	}

	public List<PredictionDTO> getPredictions() {
		return predictions;
	}

	public void setPredictions(List<PredictionDTO> predictions) {
		this.predictions = predictions;
	}
	
	public void addPrediction(PredictionDTO prediction) {
		this.predictions.add(prediction);
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public Integer getCameraId() {
		return cameraId;
	}

	public void setCameraId(Integer cameraId) {
		this.cameraId = cameraId;
	}
}

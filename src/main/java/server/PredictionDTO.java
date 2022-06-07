package server;

public class PredictionDTO {
    private String detectedAnimal;
    private Float confidence;
    private String message;
    private Boolean isPredicted;
    private String image;

	public PredictionDTO(){
    	
    }   
	
    public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getDetectedAnimal() {
		return detectedAnimal;
	}

	public void setDetectedAnimal(String detectedAnimal) {
		this.detectedAnimal = detectedAnimal;
	}

	public Float getConfidence() {
		return confidence;
	}

	public void setConfidence(Float confidence) {
		this.confidence = confidence;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Boolean getIsPredicted() {
		return isPredicted;
	}

	public void setIsPredicted(Boolean isPredicted) {
		this.isPredicted = isPredicted;
	}
}

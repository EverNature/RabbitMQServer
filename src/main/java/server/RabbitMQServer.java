package server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;
import javax.ws.rs.ProcessingException;

import org.everit.json.schema.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.LongString;

public class RabbitMQServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQServer.class);
	static final String EXCHANGE_CAMERAS = "cameras";
	static final String QUEUE_CAMERAS = "queue_camera";
    static final String DLX_EXCHANGE_NAME = "deadLetter";
    boolean stop;
    
    ConnectionFactory factory;
    ExecutorService executor;

    public RabbitMQServer() {
    	stop = false;
    	factory = new ConnectionFactory();
		try (InputStream input = new FileInputStream(FileSystems.getDefault().getPath("resources", "conf.properties").toString())) {
    	//try (InputStream input = this.getClass().getClassLoader().getResourceAsStream("resources/conf.properties")) {
			Properties prop = new Properties();
	        prop.load(input);
	        factory.setHost(prop.getProperty("host"));
	        factory.setUsername(prop.getProperty("username"));
	        factory.setPassword(prop.getProperty("password"));
		} catch (FileNotFoundException e) {
			LOGGER.error("FileNotFoundException happened");
		} catch (IOException e) {
			LOGGER.error("IOException happened at connection");
		}
		
        
        executor = Executors.newCachedThreadPool();
        
    }
    
    public void suscribe() {

        String tag = null;

        try (Connection connection = factory.newConnection()){
        	try (Channel channelCameras = connection.createChannel()) {
        		channelCameras.exchangeDeclare(EXCHANGE_CAMERAS, "fanout", true, false, false, null);
            	channelCameras.exchangeDeclare(DLX_EXCHANGE_NAME, "fanout", true, false, false, null);
            	
            	Map<String,Object> arguments = new HashMap<>();
    			arguments.put("x-dead-letter-exchange", DLX_EXCHANGE_NAME);

    			channelCameras.queueDeclare(QUEUE_CAMERAS, true, false, false, arguments);
                channelCameras.queueBind(QUEUE_CAMERAS, EXCHANGE_CAMERAS, "");

                ConsumerCameras consumer = new ConsumerCameras(channelCameras, executor);
                boolean autoack = false;
                tag = channelCameras.basicConsume(QUEUE_CAMERAS, autoack, consumer);

                LOGGER.info(" [*] Waiting for messages. To exit press Enter");
                
                synchronized (this) {
                	while(!stop) {
                		this.wait();
                	}
                }

                executor.shutdown();
                executor.awaitTermination(200, TimeUnit.SECONDS);
        	}
        } catch (NullPointerException e) {
        	LOGGER.error("NullPointerException has happened");
        } catch (IOException | TimeoutException e) {
        	LOGGER.error("IOException | TimeoutException happened");
        } catch (InterruptedException e) {
        	LOGGER.error("InterruptedException happened");
        	Thread.currentThread().interrupt();
		}
    }
    
    public synchronized void stop() {
    	stop = true;
        this.notifyAll();
    }
    
    public class ConsumerCameras extends DefaultConsumer {

		ExecutorService executor;
    	Channel channel;
    	String photosFolder;
        boolean reprocesar = false;
		boolean multiple = false;

		public ConsumerCameras(Channel channel, ExecutorService executor) {
			super(channel);
			photosFolder = FileSystems.getDefault().getPath("resources", "photos").toString();
			this.executor = executor;
			this.channel = channel;
		}
		
		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
				throws IOException {
			String message = new String(body, StandardCharsets.UTF_8);
			LOGGER.info(String.format("Received photo in (channel %d)", channel.getChannelNumber()));
			
			try {
                executor.submit(new Runnable() {
                    public void run() {
						try {
							MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
				            messageDigest.update(message.getBytes());
				            
				            String hash = ((LongString) properties.getHeaders().get("hash")).toString();
				            
				            if(hash.equals(new String(messageDigest.digest()))) {
	                        	LOGGER.info("Image arrived correctly");
	                        }
	                        else {
	                        	channel.basicNack(envelope.getDeliveryTag(), multiple, reprocesar);
	                        	LOGGER.info("Image suffered changes since original");
	                        }
				            
							byte[] photo = Base64.getDecoder().decode(message.getBytes());
	                        
	                        InputStream is = new ByteArrayInputStream(photo);
	                        BufferedImage newB = ImageIO.read(is);
	                        
	                        if(newB == null) {
	                        	LOGGER.info("Unsupported format");
	                        	channel.basicAck(envelope.getDeliveryTag(), multiple);
	                        	is.close();
	                        }
	                        else {
	                        	is.close();
								Result response = RESTClient.sendPhotos(photo);
								if(response == null) {
									LOGGER.info("No valid response received");
								}
								else {
									JSONValidation.isJsonValid(response);
									LOGGER.info("Valid JSON received");
									if(Boolean.TRUE.equals(response.getSegmented())) {
										int i = 0;
										String cameraId = ((LongString) properties.getHeaders().get("camera_id")).toString();
										String filename = ((LongString) properties.getHeaders().get("filename")).toString();
										
										RecordDTO rDto = new RecordDTO();
										rDto.setImage(message);
										rDto.setCameraId(Integer.parseInt(cameraId));
										
										for(Prediction prediction:response.getPrediction()) {
											LOGGER.info(String.format("Detected in image: %s", prediction.getClase()));
											
											InputStream is2 = new ByteArrayInputStream(Base64.getDecoder().decode(prediction.getImage().getBytes()));
											
											PredictionDTO pDto = new PredictionDTO();
					                        pDto.setImage(prediction.getImage());
					                        pDto.setConfidence(Float.parseFloat(prediction.getConfidence())*100);
					                        pDto.setDetectedAnimal(prediction.getClase());
					                        pDto.setIsPredicted(prediction.getPredicted());
					                        pDto.setMessage(prediction.getMsg());
					                        rDto.addPrediction(pDto);
					                        
					                        AnimalIsInvasor animalIsInvasor = new AnimalIsInvasor();
					                        animalIsInvasor.setAnimalName(prediction.getClase());
											if(RESTClient.checkIfInvasive(animalIsInvasor))
											{
												LOGGER.info("Invasive animal detected");
												
												BufferedImage newBi = ImageIO.read(is2);
												String uuid = UUID.randomUUID().toString();
					                        	File file = new File(FileSystems.getDefault().getPath(photosFolder, (cameraId + "_" + filename + "_" + i + "_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new java.util.Date()) + ".jpg")).toString());
						                        ImageIO.write(newBi, "jpg", file);
						                        is2.close();
						                        
						                        NodeClass nc =  new NodeClass();
						                        nc.setGuid(uuid);
						                        nc.setImagen(file.getAbsolutePath());
						                        nc.setSpecies(prediction.getClase());

						                        sendToSuscribers(nc);
						                        
						                        Files.delete(file.toPath());
											} else {LOGGER.info("Not an invasive species");}
					                        
					                        i++;
										}

				                        sendToDatabase(rDto);
				                        channel.basicAck(envelope.getDeliveryTag(), multiple);
									}
									else {
										LOGGER.info("No animal detected in the image");
										channel.basicAck(envelope.getDeliveryTag(), multiple);
									}
								}
	                        }
						} catch (IOException | NoSuchAlgorithmException | ProcessingException e) {
							LOGGER.error("IOException | NoSuchAlgorithmException | ProcessingException happened");
							try {
								channel.basicNack(envelope.getDeliveryTag(), multiple, true);
							} catch (IOException e1) {
								LOGGER.error("IOException has happened");
							}
						} catch (ValidationException e) {
							LOGGER.info("Incorrect JSON formatting");
						}
                    }

                });

            } catch (Exception e) {
            	LOGGER.error("Exception happened");
            }	
		}
		
	    public void sendToSuscribers(NodeClass nc)
	    {
	    	if(RESTClient.sendToNodeTelegram(nc)) {
	        	LOGGER.info("Prediction sent to Telegram group");
	        }
	        else { LOGGER.info("Error in sending to Telegram group"); }
	        
	        if(RESTClient.sendToNodeMail(nc)) {
	        	LOGGER.info("Prediction sent to Gmail");
	        }
	        else { LOGGER.info("Error in sending to Gmail"); }
	    }
	    
	    private void sendToDatabase(RecordDTO rDto) {
	    	if(RESTClient.sendToNodeDataBase(rDto)) {
	        	LOGGER.info("Prediction sent for storage in DB");
	        }
	        else { LOGGER.info("Error in sending to DB"); }
		}
    }
    
    public static void main(String[] args) {
    	Scanner teclado = new Scanner(System.in);
    	RabbitMQServer suscriber = new RabbitMQServer();
    	Thread hiloEspera = new Thread(() -> {
            teclado.nextLine();
            suscriber.stop();
            teclado.close();
        });
    	hiloEspera.start();
        suscriber.suscribe();
    }
}

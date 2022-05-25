package server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

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
	final static String EXCHANGE_CAMERAS = "cameras";
	final static String QUEUE_CAMERAS = "queue_camera";
    final static String DLX_EXCHANGE_NAME = "deadLetter";
    
    ConnectionFactory factory;
    ExecutorService executor;
    
    List<String> invasiveSpecies;

    public RabbitMQServer() {
    	factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("guest");
        factory.setPassword("guest");
        
        executor = Executors.newCachedThreadPool();
        invasiveSpecies = new ArrayList<>();
        invasiveSpecies.add("AFRICAN OYSTER CATCHER");  // Esto hay que sustituirlo por llamada a base de datos y tal
    }
    
    public void suscribe() {

        Channel channelCameras = null;
        try {
        	
        	Connection connection = factory.newConnection();
        	channelCameras = connection.createChannel();
        	channelCameras.exchangeDeclare(EXCHANGE_CAMERAS, "fanout");
        	channelCameras.exchangeDeclare(DLX_EXCHANGE_NAME, "fanout");
        	
        	Map<String,Object> arguments = new HashMap<>();
			arguments.put("x-dead-letter-exchange", DLX_EXCHANGE_NAME);

			channelCameras.queueDeclare(QUEUE_CAMERAS, false, false, false, arguments);
            channelCameras.queueBind(QUEUE_CAMERAS, EXCHANGE_CAMERAS, "");

            ConsumerCameras consumer = new ConsumerCameras(channelCameras, executor, invasiveSpecies);
            boolean autoack = false;
            String tag = channelCameras.basicConsume(QUEUE_CAMERAS, autoack, consumer);

            LOGGER.info(" [*] Waiting for messages. To exit press Enter");
            
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            channelCameras.basicCancel(tag);
            channelCameras.close();
            connection.close();
            
            executor.shutdown();
    		try {
    			executor.awaitTermination(200, TimeUnit.SECONDS);
    		} catch (InterruptedException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}

        } catch (IOException | TimeoutException e) {

            e.printStackTrace();
        }
    }
    
    public synchronized void stop() {
        this.notify();
    }
    
    public class ConsumerCameras extends DefaultConsumer {

		ExecutorService executor;
    	Channel channel;
    	RESTClient cliente;
    	final static String PHOTOS_FOLDER = "photos";
        boolean reprocesar = false;
		boolean multiple = false;
		List<String> invasiveSpecies;

		public ConsumerCameras(Channel channel, ExecutorService executor, List<String> invasiveSpecies) {
			super(channel);
			this.executor = executor;
			this.channel = channel;
			this.invasiveSpecies = invasiveSpecies;
	        
	        cliente =new RESTClient();
		}
		
		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
				throws IOException {
			String message = new String(body, StandardCharsets.UTF_8);

			try {
                LOGGER.info(String.format("Received photo in (channel %d)", channel.getChannelNumber()));
                
                executor.submit(new Runnable() {
                    public void run() {
						try {
							MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
				            messageDigest.update(message.getBytes());
				            
				            String hash = ((LongString) properties.getHeaders().get("hash")).toString();
				            
				            if(hash.equals(new String(messageDigest.digest()))) {
	                        	channel.basicAck(envelope.getDeliveryTag(), multiple);
	                        	LOGGER.info(String.format("Image arrived correctly"));
	                        }
	                        else {
	                        	channel.basicNack(envelope.getDeliveryTag(), multiple, reprocesar);
	                        	LOGGER.info(String.format("Image suffered changes when since original"));
	                        }
				            
							byte[] photo = Base64.getDecoder().decode(message.getBytes());
	                        
	                        InputStream is = new ByteArrayInputStream(photo);
	                        BufferedImage newB = ImageIO.read(is);
	                        
	                        if(newB == null) {
	                        	LOGGER.info(String.format("Unsupported format"));
	                        	is.close();
	                        }
	                        else {
	                        	is.close();
								Result response = cliente.sendPhotos(photo);
								if(response == null) {
									LOGGER.info(String.format("No valid response received"));
								}
								else {
									if(response.getSegmented()) {
										int i = 0;
										for(Prediction prediction:response.getPrediction()) {
											LOGGER.info(String.format("Detected in image: %s", prediction.getClase()));
											
											InputStream is2 = new ByteArrayInputStream(Base64.getDecoder().decode(prediction.getImage().getBytes()));
					                        BufferedImage newBi = ImageIO.read(is2);
											String uuid = ((LongString) properties.getHeaders().get("uuid")).toString();
											String filename = ((LongString) properties.getHeaders().get("filename")).toString();
				                        	File file = new File(FileSystems.getDefault().getPath(PHOTOS_FOLDER).toString() + "\\" + uuid + "_" + filename + "_" + i + "_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new java.util.Date()) + ".jpg");
					                        ImageIO.write(newBi, "jpg", file);
					                        is2.close();
					                        i++;
										}
									}
									else {
										LOGGER.info(String.format("No animal detected in the image"));
									}
								}
	                        }

							//channel.basicAck(envelope.getDeliveryTag(), false);
						} catch (IOException | NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
                    }
                    
                });

            } catch (Exception e) {
                LOGGER.error("", e);
                e.printStackTrace();
            }	
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

package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class RabbitMQServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQServer.class);
	final static String EXCHANGE_CAMERAS = "cameras";
    final static String EXCHANGE_CLIENT = "distributor";
    final static String DLX_EXCHANGE_NAME = "deadLetter";
	final static String DLX_QUEUE_NAME = "deadLetterQueue";
    
    ConnectionFactory factory, factory2;
    ExecutorService executor, executor2;

    public RabbitMQServer() {
    	factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("guest");
        factory.setPassword("guest");
        
        factory2 = new ConnectionFactory();
        factory2.setHost("localhost");
        factory2.setUsername("guest");
        factory2.setPassword("guest");
        
        executor = Executors.newCachedThreadPool();
        executor2 = Executors.newCachedThreadPool();
    }
    
    public void suscribe() {

        Channel channelCameras = null;
        Channel channelClient = null;
        try {
        	
        	Connection connection = factory.newConnection();
        	Connection connection2 = factory2.newConnection();
        	
        	channelCameras = connection.createChannel();
        	channelClient = connection2.createChannel();
        	
        	channelCameras.exchangeDeclare(EXCHANGE_CAMERAS, "fanout");
        	channelClient.exchangeDeclare(EXCHANGE_CLIENT, "direct");
        	channelClient.exchangeDeclare(DLX_EXCHANGE_NAME, "fanout");
            
			Map<String,Object> arguments = new HashMap<>();
			arguments.put("x-dead-letter-exchange", DLX_EXCHANGE_NAME);
			
			channelClient.queueDeclare(DLX_QUEUE_NAME, false, false,false,arguments);
			channelClient.queueBind(DLX_QUEUE_NAME,DLX_EXCHANGE_NAME,"");

            String queueName = channelCameras.queueDeclare().getQueue();
            channelCameras.queueBind(queueName, EXCHANGE_CAMERAS, "");

            ConsumerClient consumer = new ConsumerClient(channelClient, executor);
            ConsumerCameras consumer2 = new ConsumerCameras(channelCameras, executor2);
            boolean autoack = true; //CAMBIAR ESTO A FALSE PARA DAR EL TRATAMIENTO QUE QUERAMOS
            String tag = channelClient.basicConsume(DLX_QUEUE_NAME, autoack, consumer);
            String tag2 = channelCameras.basicConsume(queueName, autoack, consumer2);

            LOGGER.info(" [*] Waiting for messages. To exit press CTRL+C");
            
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            channelClient.basicCancel(tag);
            channelCameras.basicCancel(tag2);
            channelCameras.close();
            channelClient.close();
            connection.close();
            connection2.close();
            
            executor.shutdown();
            executor2.shutdown();
    		try {
    			executor.awaitTermination(200, TimeUnit.SECONDS);
    			executor2.awaitTermination(200, TimeUnit.SECONDS);
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
    
    public class ConsumerClient extends DefaultConsumer {

    	ExecutorService executor;
    	Channel channel;

		public ConsumerClient(Channel channel, ExecutorService executor) {
			super(channel);
			this.executor = executor;
			this.channel = channel;
		}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
				throws IOException {
			String message = new String(body, StandardCharsets.UTF_8);

			try {
                LOGGER.info(String.format("Received (channel %d) %s", channel.getChannelNumber(), new String(body)));

                executor.submit(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(1000);
                            LOGGER.info(String.format("Processed %s", message));
                        } catch (InterruptedException e) {
                            LOGGER.warn(String.format("Interrupted %s", message));
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.error("", e);
            }	
		}
    }
    
    public class ConsumerCameras extends DefaultConsumer {

		ExecutorService executor;
    	Channel channel;
    	RESTClient cliente;

		public ConsumerCameras(Channel channel, ExecutorService executor) {
			super(channel);
			this.executor = executor;
			this.channel = channel;
	        
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
                        byte[] photo = Base64.getDecoder().decode(message.getBytes());
						Result response = cliente.sendPhotos(photo);
						if(response == null) {
							LOGGER.info(String.format("No valid response received"));
						}
						else {
							if(response.getSegmented()) {
								for(Prediction prediction:response.getPrediction()) {
									LOGGER.info(String.format("Detected in image: %s", prediction.getClase()));
								}
							}
							else {
								LOGGER.info(String.format("No animal detected in the image"));
							}
						}
						
                    }
                });
            } catch (Exception e) {
                LOGGER.error("", e);
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

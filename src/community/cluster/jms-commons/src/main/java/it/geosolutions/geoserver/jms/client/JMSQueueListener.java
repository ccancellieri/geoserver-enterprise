/* Copyright (c) 2011 GeoSolutions - http://www.geo-solutions.it/.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package it.geosolutions.geoserver.jms.client;

import it.geosolutions.geoserver.jms.JMSApplicationListener;
import it.geosolutions.geoserver.jms.JMSEventHandler;
import it.geosolutions.geoserver.jms.JMSEventHandlerSPI;
import it.geosolutions.geoserver.jms.JMSManager;
import it.geosolutions.geoserver.jms.configuration.JMSConfiguration;
import it.geosolutions.geoserver.jms.events.ToggleType;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.listener.SessionAwareMessageListener;

/**
 * JMS Client (Consumer)
 * 
 * Class which leverages on commons classes to define a Topic consumer handling incoming messages using runtime loaded SPI to instantiate needed
 * handlers.
 * 
 * @see {@link JMSManager}
 * 
 * @author Carlo Cancellieri - carlo.cancellieri@geo-solutions.it
 * 
 */
public class JMSQueueListener extends JMSApplicationListener implements
        SessionAwareMessageListener<Message> {

    private final static Logger LOGGER = LoggerFactory.getLogger(JMSQueueListener.class);

    public JMSQueueListener() {
        super(ToggleType.SLAVE);
    }

    @Override
    public void onMessage(Message message, Session session) throws JMSException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Incoming message event for session: " + session.toString());
        }

        // FILTERING INCOMING MESSAGE
        if (!message.propertyExists(JMSConfiguration.INSTANCE_NAME_KEY))
            throw new JMSException("Unable to handle incoming message, property \'"
                    + JMSConfiguration.INSTANCE_NAME_KEY + "\' not set.");

        // check if message comes from a master with the same name of this slave
        if (message.getStringProperty(JMSConfiguration.INSTANCE_NAME_KEY).equals(
                config.getConfiguration(JMSConfiguration.INSTANCE_NAME_KEY))) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Incoming message discarded: source is equal to destination");
            }
            // if so discard the message
            return;
        }

        // check the property which define the SPI used (to serialize on the
        // server side).
        if (!message.propertyExists(JMSEventHandlerSPI.getKeyName()))
            throw new JMSException("Unable to handle incoming message, property \'"
                    + JMSEventHandlerSPI.getKeyName() + "\' not set.");

        // END -> FILTERING INCOMING MESSAGE

        // get the name of the SPI used to serialize the message
        final String generatorClass = message.getStringProperty(JMSEventHandlerSPI.getKeyName());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Incoming message was serialized using an handler generated by: \'"
                    + generatorClass + "\'");
        }

        // USING INCOMING MESSAGE
        if (message instanceof ObjectMessage) {

            final ObjectMessage objMessage = (ObjectMessage) (message);
            final Object obj = objMessage.getObject();

            if (obj instanceof Serializable) {
                final Serializable serialized = (Serializable) (obj);
                try {
                    // lookup the SPI handler, search is performed using the
                    // name
                    final JMSEventHandler<Serializable, Object> handler = JMSManager
                            .getHandlerByClassName(generatorClass);
                    if (handler == null) {
                        throw new JMSException("Unable to find SPI named \'" + generatorClass
                                + "\', be shure to load that SPI into your context.");
                    }

                    Enumeration<String> keys = message.getPropertyNames();
                    Properties options = new Properties();
                    while (keys.hasMoreElements()) {
                        String key = keys.nextElement();
                        options.put(key, message.getObjectProperty(key));
                    }
                    handler.setProperties(options);

                    // try to synchronize object locally
                    if (!handler.synchronize(handler.deserialize(serialized))) {
                        throw new JMSException("Unable to synchronize message locally.\n SPI: "
                                + generatorClass);
                    }

                } catch (Exception e) {
                    final JMSException jmsE = new JMSException(e.getLocalizedMessage());
                    jmsE.initCause(e);
                    throw jmsE;
                }

            } else {
                throw new JMSException("Bad object into ObjectMessage");
            }
        } else
            throw new JMSException("Unrecognized message type for catalog incoming event");
    }

    // /**
    // * @deprecated unused/untested
    // * @param message
    // * @throws JMSException
    // */
    // private static void getStreamMessage(Message message) throws JMSException
    // {
    // if (message instanceof StreamMessage) {
    // StreamMessage streamMessage = StreamMessage.class.cast(message);
    //
    // File file;
    // // FILTERING incoming message
    // // if (!message.propertyExists(JMSEventType.FILENAME_KEY))
    // // throw new JMSException(
    // // "Unable to handle incoming message, property \'"
    // // + JMSEventType.FILENAME_KEY + "\' not set.");
    //
    // FileOutputStream fos = null;
    // try {
    // file = new File(GeoserverDataDirectory
    // .getGeoserverDataDirectory().getCanonicalPath(), "");
    // // TODO get file name
    // // message.getStringProperty(JMSEventType.FILENAME_KEY));
    // fos = new FileOutputStream(file);
    // final int size = 1024;
    // final byte[] buf = new byte[size];
    // int read = 0;
    // streamMessage.reset();
    // while ((read = streamMessage.readBytes(buf)) != -1) {
    // fos.write(buf, 0, read);
    // fos.flush();
    // }
    // } catch (IOException e) {
    // if (LOGGER.isErrorEnabled()) {
    // LOGGER.error(e.getLocalizedMessage(), e);
    // }
    // throw new JMSException(e.getLocalizedMessage());
    // } catch (JMSException e) {
    // if (LOGGER.isErrorEnabled()) {
    // LOGGER.error(e.getLocalizedMessage(), e);
    // }
    // throw new JMSException(e.getLocalizedMessage());
    // } finally {
    // IOUtils.closeQuietly(fos);
    // }
    //
    // } else
    // throw new JMSException(
    // "Unrecognized message type for catalog incoming event");
    // }

}

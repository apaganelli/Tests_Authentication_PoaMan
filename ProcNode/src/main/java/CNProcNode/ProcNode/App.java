package CNProcNode.ProcNode;

import java.io.IOException;

import lac.cnclib.sddl.message.ApplicationMessage;
import lac.cnclib.sddl.serialization.Serialization;
import lac.cnet.radius.CheckAuthentication;
import lac.cnet.sddl.objects.ApplicationObject;
import lac.cnet.sddl.objects.Message;
import lac.cnet.sddl.objects.PrivateMessage;
import lac.cnet.sddl.udi.core.SddlLayer;
import lac.cnet.sddl.udi.core.UniversalDDSLayerFactory;
import lac.cnet.sddl.udi.core.listener.UDIDataReaderListener;

/**
 * Processing Node.
 *
 */
public class App implements UDIDataReaderListener<ApplicationObject>, Runnable
{	
	Thread t;
	SddlLayer  core;
	CheckAuthentication auth;
	long count = 0;
    int frequency = 60000;
	
	String authServer = "localhost";
	String user = "user";
	String upass = "upass";
	String secret = "secret";
	String nasId = "pn-";
	
	boolean doesAuth = true;				// do authentication or not
	
	public App(int id) {   	
	    this(id, 60000, true, "192.168.56.101");
    }
	
    public App(int id, int freq, boolean doesAuth, String address) {
    	nasId += Integer.toString(id);
		this.doesAuth     = doesAuth;
		this.authServer   = address;
		this.frequency    = freq;
	
    	t = new Thread(this, nasId);
    	t.start();
	}
		
	@Override
	public void run() {
		
		if(doesAuth) {
			auth = new CheckAuthentication(authServer, user, upass, nasId, secret);
    	
			if( ! auth.Authorize() ) {
				System.exit(-1);
			}
		}
		
	    core = UniversalDDSLayerFactory.getInstance();
	    core.createParticipant(UniversalDDSLayerFactory.CNET_DOMAIN);

	    core.createPublisher();
	    core.createSubscriber();

	    Object receiveMessageTopic = core.createTopic(Message.class, Message.class.getSimpleName());
	    core.createDataReader(this, receiveMessageTopic);

	    Object toMobileNodeTopic = core.createTopic(PrivateMessage.class, PrivateMessage.class.getSimpleName());
	    core.createDataWriter(toMobileNodeTopic);

	    ////System.out.println(" === " + nasId + " Server Started ===");
	    
	    synchronized (this) {
	      try {
	        this.wait();
	      } catch (InterruptedException e) {
	        e.printStackTrace();
	      }
	    }    			
	}
       
	@Override
	public void onNewData(ApplicationObject topic) {	    
    	Message message = (Message) topic;    	
    	String content = (String) Serialization.fromJavaByteStream(message.getContent());
    
    	String[] txt = content.split(":");
    	String msgInfo = "Received OK";

    	PrivateMessage privateMessage = new PrivateMessage();
    	privateMessage.setGatewayId(message.getGatewayId());
    	privateMessage.setNodeId(message.getSenderId());    
    	ApplicationMessage appMsg = new ApplicationMessage();
    	    	
    	if(txt.length > 0) {
        	System.out.println(nasId + " received " + " - " + txt[3]);
        	
			String[] msg = txt[3].split("_");			
			// Is it the first message?
			if(msg.length > 0 && msg[1].equals("0")) {
			    msgInfo = "frequency_" + frequency;
			}
		
	    	if(doesAuth) {
	    		if(auth.CheckHMAC(content)) {
		    		////System.out.println(nasId +  " Check OK: " + txt[3] + "\n");		    						    
			    	msgInfo = auth.addHMAC(msgInfo, nasId);			    
	    		} else {
		    		////System.out.println(nasId + " Check NOK: " + txt[3] + "\n");
	    			return;
		    	}
	    	}     	
	    }
    	
		try {
			appMsg.setContent(Serialization.toJavaByteStream(msgInfo));
		} catch (IOException e) {
			e.printStackTrace();
		}		    	

    	privateMessage.setMessage(Serialization.toProtocolMessage(appMsg));
    	core.writeTopic(PrivateMessage.class.getSimpleName(), privateMessage);	    	
		count++; 	    
	}
		
	public long getCount() { return count; }
}

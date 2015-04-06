package com.example.home;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {

	private final static String macAddress = "00.1D.60.88.57.46";
	//private final static String macAddress = "7C.E9.D3.20.78.5E";
	private final static int PORT = 9; //Wake-On Lan port
	public final static String LOG_TAG = "HOME";
	private final static String HOME = "\"Stanley\"";
	private final static int TIMEOUT = 100;
	private static String staticIpAddress = "192.168.1.127";
	
	private TextView wakeButton;
	private TextView startRemoteButton;
	private TextView playButton;
	private TextView nextButton;
	private TextView previousButton;
	private TextView quitButton;
	private TextView randomButton;
	private static InetAddress remoteComputer;
    private final static ArrayList<InetAddress> possibleRemoteComputers = new ArrayList<InetAddress>();
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wakeButton = (TextView) findViewById(R.id.myButton);
        startRemoteButton = (TextView) findViewById(R.id.startButton);
        playButton = (TextView) findViewById(R.id.playButton);
        nextButton = (TextView) findViewById(R.id.nextButton);
        previousButton = (TextView) findViewById(R.id.previousButton);
        quitButton = (TextView) findViewById(R.id.quitButton);
        randomButton = (TextView) findViewById(R.id.randomButton);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    private static byte[] getAddressBytes (String address, int radix) throws IllegalArgumentException {
		String[] string = address.split("\\.");
		byte[] bytes = new byte[string.length];

		for (int i = 0; i < string.length; i++) {
			bytes[i] = (byte) Integer.parseInt(string[i], radix);
		}

		return bytes;

	}
    
    private boolean checkConnectivity() {
    	ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    	NetworkInfo networkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
    	if (networkInfo != null && networkInfo.isConnected()) {
    		WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
    		String ssid = wm.getConnectionInfo().getSSID();
    		if (!ssid.equals(HOME)) {
    			Log.e(MainActivity.LOG_TAG, "SSID should be: " + HOME + ", but is: " + ssid + ".");
    			return false;
    		}
    		return true;
    	}
    	return false;
    }
    
    /** Called when the user clicks the wake button */
    public void handleWake(View view) {
    	if(!checkConnectivity()) {
    		wakeButton.setText("Failure - Not Connected to 'Stanley' network");
    		return;
    	}
    	ExecuteWake ew = new ExecuteWake();
    	ew.execute();
    }
    
    /*
     *  The following handle*** methods are called when the appropriate button is clicked
     */    
    
    public void handleStartRemote(View view) {
    	sendMessage(Message.MAGIC);
    }
    
    public void handlePlay(View view) {
    	sendMessage(Message.PLAY);
    }
    
    public void handleNext(View view) {
    	sendMessage(Message.NEXT);
    }
    
    public void handlePrevious(View view) {
    	sendMessage(Message.PREVIOUS);
    }
    
    public void handleQuit(View view) {
    	sendMessage(Message.QUIT);
    }
    
    public void handleRandom(View view) {
    	sendMessage(Message.RANDOM);
    }
    
    //initializes the logic to send a packet to the computer
    public void sendMessage(String message) {
    	/*
    	if(!checkConnectivity()) {
    		return;
    	}
    	*/
    	ExecuteMediaControl er = new ExecuteMediaControl();
    	er.execute(message);
    }
    
    private class Output {
    	private String type;
    	private boolean success;
        private InetAddress address;
    	
    	public Output(String type, boolean success, InetAddress address) {
    		this.type = type;
    		this.success = success;
            this.address = address;
    	}
    	
    	public String getType() {
    		return type;
    	}
    	
    	public boolean getSuccess() {
    		return success;
    	}

        public InetAddress getAddress() {
            return address;
        }
    }

    /**
     * Searches the network for all possible computers to send packets to.
     * IMPORTANT: Because there is no easy way to map from network addresses to physical addresses in Android,
     * there isn't a definitive way to select the exact remote computer we want to send our packets to.
     * NOTE: Currently we use a static ip address.
     */
    private void findAllRemoteComputers() {
        try {
            remoteComputer = InetAddress.getByName(staticIpAddress);
            possibleRemoteComputers.add(remoteComputer);
        } catch (Exception e) {
            // falls through
        }

        /**
        if (possibleRemoteComputers != null) {
            return;
        }
    	ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    	NetworkInfo networkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
    	if (networkInfo == null ||  !networkInfo.isConnected()) {
    		return;
    	}
    	WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
    	int networkAddr = wm.getConnectionInfo().getIpAddress();
    	possibleRemoteComputers = scanNetwork(networkAddr);
         */
    }
    
    //scans a networkAddr for a list of all reachable ipAddresses on that network
    private ArrayList<InetAddress> scanNetwork(int networkAddr) {
        ArrayList<InetAddress> output = new ArrayList<InetAddress>();
    	for (int i = 1; i < 255; i++) {
    		String deviceAddrStr = String.format("%d.%d.%d.%d",
    				(networkAddr & 0xff),
    				(networkAddr >> 8 & 0xff),
    				(networkAddr >> 16 & 0xff),
    				i);
    		InetAddress deviceAddr;
			try {
				deviceAddr = InetAddress.getByName(deviceAddrStr);
				if (deviceAddr.isReachable(TIMEOUT)) {
					Log.d(MainActivity.LOG_TAG, "Debug - Found device: " + deviceAddr.toString());
	    			output.add(deviceAddr);
	    		}
			} catch (UnknownHostException e) {
				//falls through
			} catch (IOException e) {
				//falls through
			}
    	}
    	return output;
    }
    
    private class ExecuteMediaControl extends AsyncTask<String, Void, ArrayList<Output>> {

		@Override
		protected ArrayList<Output> doInBackground(String... cmd) {
            ArrayList<Output> output = new ArrayList<Output>();
			//send the 'cmd' message to the remote computer
            //TODO: add in an intermediate step before media controls are possible so we don't have to
            //send packets to each possibleRemoteComputer.
            for (InetAddress remoteComputer : possibleRemoteComputers) {
                try {
                    if (remoteComputer == null) {
                        Log.e(MainActivity.LOG_TAG, "Error - Could not find remoteComputer on Network");
                        output.add(new Output(cmd[0], false, remoteComputer));
                    }
                    Log.d(MainActivity.LOG_TAG, "Debug - Sending media control packet to :" + remoteComputer);
                    Socket connection = new Socket(remoteComputer, PORT);
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
                    writer.write(cmd[0], 0, cmd[0].length());
                    writer.newLine();
                    writer.flush();
                    connection.close();
                } catch (UnknownHostException e) {
                    Log.e(MainActivity.LOG_TAG, "Error - Unknown host");
                    output.add(new Output(cmd[0], false, remoteComputer));
                } catch (IOException e) {
                    Log.e(MainActivity.LOG_TAG, "Error - IO Exception");
                    Log.e(MainActivity.LOG_TAG, e.getMessage());
                    output.add(new Output(cmd[0], false, remoteComputer));
                }
                output.add(new Output(cmd[0], true, remoteComputer));
            }
            return output;
		}

		protected void onPostExecute(ArrayList<Output> result) {
			TextView correctButton = null;
			String buttonName = result.get(0).getType();
			if (buttonName.equals(Message.MAGIC)) {
				correctButton = startRemoteButton;
			} else if(buttonName.equals(Message.PLAY)) {
				correctButton = playButton;
			} else if(buttonName.equals(Message.NEXT)) {
				correctButton = nextButton;
			} else if(buttonName.equals(Message.PREVIOUS)) {
				correctButton = previousButton;
			} else if(buttonName.equals(Message.QUIT)) {
				correctButton = quitButton;
			} else if (buttonName.equals(Message.RANDOM)) {
				correctButton = randomButton;
			} else {
				startRemoteButton.setText("FAILURE - FATAL, BAD BAD BAD");
			}
            //TODO: see doInBackground TODO
            boolean success = false;
            for (Output o : result) {
                if (o.getSuccess() == true) {
                    success = true;
                } else {
                    possibleRemoteComputers.remove(o.getAddress());
                }
            }

			if (!success) {
				correctButton.setText("FAILURE - Could not send packet");
			}
			//reset button's text after 3 seconds
			final TextView button = correctButton;
			final String buttonText = buttonName;
			Handler h = new Handler();
    		h.postDelayed(new Runnable() {
    	         public void run() {
    	        	 button.setText(buttonText);
    	         }
    	    }, 3000);
		}

    }

    /**
     * A class to asynchronously execute sending a magic wake packet to the remote computer.
     * Since we don't know exactly what network address we are trying to wake, we keep track of all
     * successes or failures using a hashmap.
     */
    private class ExecuteWake extends AsyncTask<Void, Void, HashMap<InetAddress, Boolean>> {

		@Override
		protected HashMap<InetAddress, Boolean> doInBackground(Void... params) {
            // Create the magic packet using the macAddress of the computer we're looking for.
            HashMap<InetAddress, Boolean> output = new HashMap<InetAddress, Boolean>();
			byte[] macAddressBytes = getAddressBytes(macAddress, 16);
			byte[] bytes = new byte[6 + 16*macAddressBytes.length];

			for (int i = 0; i < 6; i++) {
				bytes[i] = (byte) 0xff;
			}

			for (int i = 6; i < bytes.length; i++) {
				bytes[i] = macAddressBytes[i % 6];
			}

            findAllRemoteComputers();

            for (InetAddress ipAddress : possibleRemoteComputers) {
                try {
                    Log.d(MainActivity.LOG_TAG, "Debug - sending packet to : " + ipAddress);
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, ipAddress,80);
                    DatagramSocket socket = new DatagramSocket();
                    socket.send(packet);
                    socket.close();
                } catch(UnknownHostException e) {
                    Log.e(MainActivity.LOG_TAG, "Error - Unknown host");
                    output.put(ipAddress, false);
                } catch(SocketException e) {
                    Log.e(MainActivity.LOG_TAG, "Error - Socket Exception");
                    Log.e(MainActivity.LOG_TAG, e.getMessage());
                    output.put(ipAddress, false);
                } catch(IOException e) {
                    Log.e(MainActivity.LOG_TAG, "Error - IO Exception");
                    output.put(ipAddress, false);
                }
                output.put(ipAddress, true);
            }
            return output;
		}
		
		// onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(HashMap<InetAddress, Boolean> result) {
            boolean packetSent = false; // was a wake packet successfully sent somewhere?
            for (Map.Entry<InetAddress, Boolean> entry : result.entrySet()) {
                if (entry.getValue() == false) {
                    possibleRemoteComputers.remove(entry.getKey());
                } else {
                    packetSent = true;
                }
            }
        	if (packetSent == true) {
        		wakeButton.setText("Success!");
        		Handler h = new Handler();
        		h.postDelayed(new Runnable() { 
        	         public void run() { 
        	        	 wakeButton.setText("Turn On Computer"); 
        	         } 
        	    }, 10000);
        	} else {
        		wakeButton.setText("FAILURE - Could not send packet");
        	}
            
       }

    }
    
    //Class for communication with remote computer
    private class Message {
    	final static String PLAY = "play";
    	final static String NEXT = "next";
    	final static String PREVIOUS = "previous";
    	final static String QUIT = "quit";
    	final static String MAGIC = "Turn On";
    	final static String RANDOM = "random";
    	
    	private String myMessage;
    	
    	public Message(String message) {
    		myMessage = message;
    	}
    	
    	public String getMessage() {
    		return myMessage;
    	}

    }
    
}

package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

import org.apache.commons.validator.Validator;
import org.apache.commons.validator.routines.InetAddressValidator;

/**
 * Client-side software for the CoffeeChat program.
 * @author Matti
 *
 */
public class JClient {
	
	/**
	 * Output pane that displays messages received. Is encompassed by
	 * msgOutputScrollable. Messages are directly added to this.
	 */
	private static JTextPane msgOutput;
	
	/**
	 * A pane that encompasses msgOutput, allowing a vertical scroll bar
	 * to be used. Display modifications are done to this.
	 * @see msgOutput
	 */
	private static JScrollPane msgOutputScrollable;
	
	/**
	 * Section for message input by user. User can press the ENTER key
	 * to submit their message for further processing.
	 */
	private static JTextPane msgInput;
	
	/**
	 * Holy fuck I know nothing of networking
	 */
	private static Socket clientSocket;
	
	/**
	 * Whether or not the user is connected to a server.
	 */
	private static boolean connectedToServer = false;
	
	/**
	 * A compiled list of messages sent to the client in order of time (earliest to latest).
	 * TODO: Create alternative to String.
	 */
	private static ArrayList<String> messageStack = new ArrayList<String>();
	
	/**
	 * Message of the day. Meant to provide first time users with information
	 * on how the system as a whole works while simultaneously not doing so.
	 */
	private static final String clientMotD = "Hello! Enter an IP address headed with \"/c\" to attempt to connect to a jChat server. Any messages sent without a valid connection will be sent back to yourself. For more information, type \"/h\".";
	
	/**
	 * 
	 */
	private static PrintWriter toServer;
	
	/**
	 * A thread that adds messages sent from the server onto the messageStack.
	 * Starts when a valid connection to a JChat server is made.
	 * @see messageStack
	 */
	private static Thread writeToClient;
	
	/**
	 * Initializes the client GUI. Also provides the event handler for the enter key,
	 * which submits the message in msgInput for further processing.
	 * @param args
	 */
	public static void main(String args[]) {
		JFrame chatWindow = new JFrame("Coffee Talk 0.1");
		chatWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		chatWindow.setMinimumSize(new Dimension(300, 400));
		chatWindow.setLocationRelativeTo(null);
		chatWindow.setResizable(false);
		
		chatWindow.setLayout(new BoxLayout(chatWindow.getContentPane(), BoxLayout.Y_AXIS));
		
		JPanel outputWrapper = new JPanel(new BorderLayout());
		outputWrapper.setBackground(new Color(220, 220, 220));
		
		msgOutput = new JTextPane();
		msgOutput.setEditable(false);
		msgOutput.setBackground(new Color(220, 220, 220));
		msgOutput.setEditorKit(new WrapEditorKit());
		msgOutputScrollable = new JScrollPane(msgOutput, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		msgOutputScrollable.setMinimumSize(new Dimension(300, 300));
		msgOutputScrollable.setPreferredSize(new Dimension(300, 300));
		msgOutputScrollable.setMaximumSize(new Dimension(300, 300));
		msgOutputScrollable.setBorder(BorderFactory.createEmptyBorder());	
		outputWrapper.add(msgOutputScrollable, BorderLayout.SOUTH);
		chatWindow.add(outputWrapper);
		
		msgInput = new JTextPane();
		msgInput.setMinimumSize(new Dimension(300, 100));
		msgInput.setPreferredSize(new Dimension(300, 100));
		msgInput.setMaximumSize(new Dimension(300, 100));
		msgInput.setBackground(new Color(240, 240, 240));
		msgInput.setEditorKit(new WrapEditorKit());
		
		@SuppressWarnings("serial")
		Action submit = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String msg = msgInput.getText().trim();
				if(!msg.equals("")) {
					localParse(msg);
				}
			}
		};
		
		msgInput.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "enter");
		msgInput.getActionMap().put("enter", submit);
		
		chatWindow.add(msgInput);
		
		chatWindow.pack();
		chatWindow.setVisible(true);
		
		updateOutput(clientMotD);
	}
	
	/**
	 * Adds a message to the messageStack; after this is done, refreshes the display,
	 * showing the just-included message along with the previous messages.
	 * @param msg The newest message to add and display.
	 */
	public static void updateOutput(String msg) {
		messageStack.add(msg);
		//Don't add newline after previous message if there exists only one message.
		if(messageStack.size() == 1) {
			msgOutput.setText(msgOutput.getText() + messageStack.get(messageStack.size() - 1));
		} else {
			msgOutput.setText(msgOutput.getText() + "\n" + messageStack.get(messageStack.size() - 1));
		}
		
		msgOutput.setCaretPosition(msgOutput.getDocument().getLength());
	}
	
	/**
	 * Locally parses non-empty submitted Strings from msgInput. Any messages that lead with
	 * "/" are commands that are either executed client-side or server-side.
	 * All commands have a single parameter, delimited by a space. This is bound to change
	 * in later versions.
	 * 
	 * If a non-command is sent (i.e. a normal message), it is echoed back to the user
	 * if not connected to a server. If the user is connected to a server, it is broadcast 
	 * to all users.
	 * 
	 * Note that the minimum length of an entered string must be one character.
	 * This can be assumed in this method.
	 * @param strToParse The string sent in for parsing.
	 */
	public static void localParse(String strToParse) {
		msgInput.setText("");
		String[] messageSplit = strToParse.split(" ");
		
		//Accesses manual pages using the preceding ?.
		if(messageSplit[0].charAt(0) == '?') {
			echo(strToParse);
			if(messageSplit[0].length() > 1) {
				updateOutput(Manual.getHelpPage(messageSplit[0].substring(1)));
			} else {
				updateOutput("Follow your ? with a command (such as \"?/c\" to get more information on its use.");
			}
			
			return;
		}
		
		echo(strToParse);
		
		//Note: the first element references the / commands. If it doesn't, it is a normal message.
		switch(messageSplit[0]) {
			
			//Handled client-side. Attempts to connect to server with IP address
			//indicated by first parameter.
			case "/c":
			case "/connect":
				if(messageSplit.length != 1) {
					connectToServer(messageSplit[1]);
				} else {
					updateOutput("You must enter a valid IPv4 address following the /c command (e.g. /c 192.168.1.3).");
				}
				break;
			
			//Handled client-side. Attempts to disconnect from server. Seeing
			//as it directly attempts to close the socket, things tend to get a touch messy.
			//Takes no parameters.
			case "/d":
			case "/dc":
			case "/disconnect":
				if(connectedToServer) {
					String ip = clientSocket.getInetAddress().toString();
					connectedToServer = false;
					try {
						clientSocket.close();
						updateOutput("Successfully disconnected from server at " + ip);
					} catch(IOException ioe) {
						ioe.printStackTrace();
						updateOutput("There was a problem disconnecting from the server. Restart this application.");
					}
				}
				else {
					updateOutput("You're not connected to a server!");
				}
				break;
			
			//Handled client-side. Attempts to open the shorthand command list
			//in the default text editor.
			case "/h":
			case "/help":
				if(Desktop.isDesktopSupported()) {
					try {
						Desktop.getDesktop().open(new File("help/commandlist.txt"));
					} catch(IOException ioe) {
						updateOutput("Could not successfully open command list.");
					}
				}
				break;
				
				
			//Handled either server-side or client-side. If the user is not connected,
			//message is echoed. Else, broadcasts message.
			default:
				if(connectedToServer) {
					toServer.println(strToParse);
				} else {
					
				}
		}
	}
	
	public static boolean connectToServer(String ip) {
		InetAddressValidator v = new InetAddressValidator();
		if(v.isValidInet4Address(ip)) {
			try {
				clientSocket = new Socket();
				clientSocket.setSoTimeout(5000);
				clientSocket.connect(new InetSocketAddress(ip, 52682), 5000);
				
				toServer = new PrintWriter(clientSocket.getOutputStream(), true);
				
				writeToClient = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							BufferedReader fromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
							String message;
							while((message = fromServer.readLine()) != null) {
								JClient.updateOutput(message);
							}
						} catch(IOException ioe) {
							ioe.printStackTrace();
						} finally {
							try {
								if(!clientSocket.isClosed()) {
									clientSocket.close();
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				});
				
				writeToClient.start();
				connectedToServer = true;
				
			} catch (UnknownHostException e) {
				e.printStackTrace();
				updateOutput("Cannot connect to " + ip + ".");
			} catch (IOException e) {
				e.printStackTrace();
				updateOutput("Cannot connect to " + ip + ".");
			}	
			return true;
		} else {
			updateOutput("Cannot connect to " + ip + ".");
			return false;
		}
	}

	/**
	 * Relays a message exclusively to the user that sent it.
	 * @param msg Message to send to self.
	 */
	private static void echo(String msg) {
		updateOutput("[Self]: " + msg);
	}
}
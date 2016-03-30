package client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public final class Manual {
	private static HashMap<String, String> manualPages;
	
	static {
		manualPages = new HashMap<String, String>();
		initializeManual();
	}
	
	private static void initializeManual() {
		try {
			BufferedReader br = new BufferedReader(new FileReader("help/help.txt"));
			StringBuilder currDesc = new StringBuilder("\n");
			String currLine = "";
			
			while((currLine = br.readLine()) != null) {
				if(currLine.equals("::ENDPAGE::")) {	//Clears the Description StringBuilder.
					currDesc.setLength(0);
					currDesc.append("\n");
				} else if(currLine.length() >= 3 && currLine.substring(0, 3).equals(":::")) {	//If text is associated command 
					manualPages.put(currLine.substring(3), currDesc.toString());
				} else {	//If text is part of description
					currDesc.append(currLine + "\n");
				}
			}

			br.close();
		} catch(IOException ioe) {
			
		}
	}
	
	public static String getHelpPage(String command) {
		String desc = manualPages.get(command);
		return desc != null ? desc : "\nCommand \"" + command + "\" not found.\n";
	}
}
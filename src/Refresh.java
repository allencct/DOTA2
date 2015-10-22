import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import weka.attributeSelection.PrincipalComponents;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;


public class Refresh {
	private List<String> pickedOrNot = Arrays.asList("Picked","Not Picked");
	private HashMap<String, Attribute> heroMap;
	private String steamKey;
	private final boolean refreshBoth = true;
	private final boolean onlyIds = true;
	private boolean onlyPro = true;
	private int timeout = 30*1000; //first number is seconds
	private String proMatchIds = "proMatchIds";
	private String allMatchIds = "allMatchIds";
	private final String USER_AGENT = "Mozilla/5.0";
			
	public Refresh(){
		try {
			steamKey = readDataFile("SteamKey.txt").readLine();
		} catch (IOException e) {
			System.out.println("No Steam Key");
			e.printStackTrace();
		}
	}
	
	private void refreshFiles(){
		try {
			refreshIds();
			if(!onlyIds){
				createArff();
				createModel();
			}
			if(refreshBoth){
				onlyPro = !onlyPro;
				refreshIds();
				if(!onlyIds){
					createArff();
					createModel();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private JSONObject sendGet(String url) throws Exception {
				  
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
 
		// optional default is GET
		con.setRequestMethod("GET");
 
		//add request header
		con.setRequestProperty("User-Agent", USER_AGENT);
 
		int responseCode = con.getResponseCode();
//		System.out.println("\nSending 'GET' request to URL : " + url);
//		System.out.println("Response Code : " + responseCode);
		
		BufferedReader streamReader = new BufferedReader(new InputStreamReader(con.getInputStream())); 
		StringBuilder responseStrBuilder = new StringBuilder();

		String inputStr;
		while ((inputStr = streamReader.readLine()) != null)
		    responseStrBuilder.append(inputStr);
		
		//print result
		JSONObject json = new JSONObject(responseStrBuilder.toString());
		return json; 
	}
	
	private void createArff() throws Exception{
		ArrayList<String> matchIds = readIds();
		if(matchIds == null)
			System.out.println("JK, got issues");
		ArrayList<Attribute> picks = fillPicksAtts();
				
		Instances data = new Instances("Team Comp", picks, picks.size());
		
		HashMap<Integer, String> idToHero = idToHero();
		
		String matchUrl = "https://api.steampowered.com/IDOTA2Match_570/GetMatchDetails/V001/?key=" + steamKey + "&match_id=";
		
		for(String id : matchIds){
			Instance matchResult = new DenseInstance(picks.size());
			matchResult.setDataset(data);
			fillInstance(matchResult);
			
			JSONObject match = (JSONObject) sendGet(matchUrl + id).get("result");		
			//Captain's Mode Only
			try{
				if(!match.has("picks_bans"))
					continue;
				JSONArray picksBans = match.getJSONArray("picks_bans");
				for(Object heroObj : picksBans){
					JSONObject heroDetails = (JSONObject)heroObj;
					if((boolean)heroDetails.get("is_pick")){
						String hero = idToHero.get(heroDetails.get("hero_id"));
						String team = (Integer)heroDetails.get("team") == 0 ? "Radiant" : "Dire";
						matchResult.setValue(heroMap.get(team + ":" + hero), "Picked");
					}else{
						continue;
					}
				}
			}catch(JSONException e){
				e.printStackTrace();
				System.out.println("Could not get data for game " + id);
				System.out.println(matchUrl);
				continue;
			}
			matchResult.setValue(heroMap.get("Victory"), (boolean)match.get("radiant_win") ? "Radiant" : "Dire");
			data.add(matchResult);
		}
		ArffSaver saver = new ArffSaver();
	    saver.setInstances(data);
	    saver.setFile(new File("./data/" + (onlyPro ? proMatchIds : allMatchIds) + ".arff"));
	    saver.writeBatch();
	    System.out.println(onlyPro ? "Arff file created for pro matches" : "Arff file created for all matches");
		//System.out.println(data);
	}
	
	private void createModel() throws Exception{
		Instances data = new Instances(readDataFile("./data/" + (onlyPro ? proMatchIds : allMatchIds)  + ".arff"));
    	data.setClassIndex(data.numAttributes() - 1);

    	PrincipalComponents pca = new PrincipalComponents();
    	pca.buildEvaluator(data);
    	weka.core.SerializationHelper.write("./data/" + (onlyPro ? proMatchIds : allMatchIds)  + "_PCA.model", pca);
    	System.out.println(onlyPro ? "PCA Model created for pro matches" : "PCA Model created for all matches");
	}
	
	private void fillInstance(Instance matchResult){
		//matchResult.setValue(heroMap.get("Victory"), (boolean)match.get("radiant_win") ? "Radiant" : "Dire");
		for (Attribute attr : heroMap.values()) {
			matchResult.setValue(attr, attr.value(1));
		}
	}
	
	private HashMap<Integer, String> idToHero() throws Exception{
		JSONArray allHeroes = (JSONArray)((JSONObject)sendGet("https://api.steampowered.com/IEconDOTA2_570/GetHeroes/v0001/?key=" + steamKey + "&language=en_us").get("result")).get("heroes");
		
		HashMap<Integer, String> idToHero = new HashMap<Integer, String>();
		
		for(Object heroObj : allHeroes){
			JSONObject hero = (JSONObject)heroObj;
			idToHero.put((Integer) hero.get("id"), ((String) hero.get("localized_name")).replaceAll("\\s",""));
		}
		return idToHero;
	}

	private ArrayList<String> readIds(){
		ArrayList<String> matchIds = new ArrayList<String>();
		String matchIdSrc = "./data/" + (onlyPro ? proMatchIds : allMatchIds)  + ".txt";
		try{
			BufferedReader br = new BufferedReader(new FileReader(matchIdSrc));
	    	String line = br.readLine();
	        while (line != null) {
	    		String[] subparts = line.split(" ");
	        	matchIds.add(subparts[0]);
	            line = br.readLine();
	        }
	    } catch (Exception e) {
			//e.printStackTrace();
	    	System.out.println("First time creating Ids");
			return null;
		}
		return matchIds;
	}
	
	private void refreshIds() throws IOException{		
		ArrayList<String> matchIds = new ArrayList<String>();
		ArrayList<String> prevMatchIds = new ArrayList<String>();
		Document matchPage = null;
		String matchURL = "http://www.dotabuff.com/esports/matches";
		Elements matches;
		
		//get IDs
		try{
			matchPage = Jsoup.connect(matchURL).userAgent("Chrome").timeout(timeout).get();
		}catch(Exception e){
			
		}
				
		matches = matchPage.select("tr");
		for(int i = 1; i < matches.size(); i++){
			String matchInfo = matches.get(i).text();
			if(!matchInfo.contains("Amateur") || !onlyPro){
				String id = matchInfo.substring(0, 10) + " " + matchInfo.substring(10, 21);
				//matchPage = Jsoup.connect("http://www.dotabuff.com/matches/" + id.split(" ")[0]).userAgent("Chrome").timeout(timeout).get();
				//if(matchPage.select("dd").get(1).text().contains("Captains Mode")){
				matchIds.add(id);
				//}
			}
		}
		for(int page = 2; page < 51; page++){
			try{
				matchPage = Jsoup.connect(matchURL + "?page=" + page).timeout(timeout).userAgent("Chrome").get();
				matches = matchPage.select("tr");
				for(int i = 1; i < matches.size(); i++){
					String matchInfo = matches.get(i).text();
					if(!matchInfo.contains("Amateur") || !onlyPro){
						String id = matchInfo.substring(0, 10) + " " + matchInfo.substring(10, 21);
						//matchPage = Jsoup.connect("http://www.dotabuff.com/matches/" + id.split(" ")[0]).userAgent("Chrome").timeout(timeout).get();
						//if(matchPage.select("dd").get(1).text().contains("Captains Mode")){
						matchIds.add(id);
						//}
					}
				}
			}catch(HttpStatusException e){
				System.out.println("Page: " + page);
				page--;
				//e.printStackTrace();
			}
		}
		
		prevMatchIds = readIds();
		//System.out.println(matchIds.size());
		//save IDs
		PrintWriter out = null;
		String matchIdDest = "./data/" + (onlyPro ? proMatchIds : allMatchIds)  + ".txt";
		out = new PrintWriter(new FileWriter(matchIdDest, true));
		for(int i = matchIds.size()-1; i >= 0; i--){
    		String[] subparts = matchIds.get(i).split(" ");
			if(prevMatchIds != null && !prevMatchIds.contains(subparts[0]))
				out.println(matchIds.get(i));
		}
		out.close();
		
	    System.out.println(onlyPro ? "Ids refreshed for pro matches" : "Ids refreshed for all matches");
	}
	
	private ArrayList<Attribute> fillPicksAtts(){
		heroMap = new HashMap<String, Attribute>();
		ArrayList<Attribute> atts = new ArrayList<Attribute>();

		Attribute att = new Attribute("Radiant: Abaddon", pickedOrNot);
		heroMap.put("Radiant:Abaddon", att);
		atts.add(att);
		att = new Attribute("Radiant: Abyssal Underlord", pickedOrNot);
		heroMap.put("Radiant:AbyssalUnderlord", att);
		atts.add(att);
		att = new Attribute("Radiant: Alchemist", pickedOrNot);
		heroMap.put("Radiant:Alchemist", att);
		atts.add(att);
		att = new Attribute("Radiant: Ancient Apparition", pickedOrNot);
		heroMap.put("Radiant:AncientApparition", att);
		atts.add(att);
		att = new Attribute("Radiant: Anti-Mage", pickedOrNot);
		heroMap.put("Radiant:Anti-Mage", att);
		atts.add(att);
		att = new Attribute("Radiant: Arc Warden", pickedOrNot);
		heroMap.put("Radiant:ArcWarden", att);
		atts.add(att);
		att = new Attribute("Radiant: Axe", pickedOrNot);
		heroMap.put("Radiant:Axe", att);
		atts.add(att);
		att = new Attribute("Radiant: Bane", pickedOrNot);
		heroMap.put("Radiant:Bane", att);
		atts.add(att);
		att = new Attribute("Radiant: Batrider", pickedOrNot);
		heroMap.put("Radiant:Batrider", att);
		atts.add(att);
		att = new Attribute("Radiant: Beastmaster", pickedOrNot);
		heroMap.put("Radiant:Beastmaster", att);
		atts.add(att);
		att = new Attribute("Radiant: Bloodseeker", pickedOrNot);
		heroMap.put("Radiant:Bloodseeker", att);
		atts.add(att);
		att = new Attribute("Radiant: Bounty Hunter", pickedOrNot);
		heroMap.put("Radiant:BountyHunter", att);
		atts.add(att);
		att = new Attribute("Radiant: Brewmaster", pickedOrNot);
		heroMap.put("Radiant:Brewmaster", att);
		atts.add(att);
		att = new Attribute("Radiant: Bristleback", pickedOrNot);
		heroMap.put("Radiant:Bristleback", att);
		atts.add(att);
		att = new Attribute("Radiant: Broodmother", pickedOrNot);
		heroMap.put("Radiant:Broodmother", att);
		atts.add(att);
		att = new Attribute("Radiant: Centaur Warrunner", pickedOrNot);
		heroMap.put("Radiant:CentaurWarrunner", att);
		atts.add(att);
		att = new Attribute("Radiant: Chaos Knight", pickedOrNot);
		heroMap.put("Radiant:ChaosKnight", att);
		atts.add(att);
		att = new Attribute("Radiant: Chen", pickedOrNot);
		heroMap.put("Radiant:Chen", att);
		atts.add(att);
		att = new Attribute("Radiant: Clinkz", pickedOrNot);
		heroMap.put("Radiant:Clinkz", att);
		atts.add(att);
		att = new Attribute("Radiant: Clockwerk", pickedOrNot);
		heroMap.put("Radiant:Clockwerk", att);
		atts.add(att);
		att = new Attribute("Radiant: Crystal Maiden", pickedOrNot);
		heroMap.put("Radiant:CrystalMaiden", att);
		atts.add(att);
		att = new Attribute("Radiant: Dark Seer", pickedOrNot);
		heroMap.put("Radiant:DarkSeer", att);
		atts.add(att);
		att = new Attribute("Radiant: Dazzle", pickedOrNot);
		heroMap.put("Radiant:Dazzle", att);
		atts.add(att);
		att = new Attribute("Radiant: Death Prophet", pickedOrNot);
		heroMap.put("Radiant:DeathProphet", att);
		atts.add(att);
		att = new Attribute("Radiant: Disruptor", pickedOrNot);
		heroMap.put("Radiant:Disruptor", att);
		atts.add(att);
		att = new Attribute("Radiant: Doom", pickedOrNot);
		heroMap.put("Radiant:Doom", att);
		atts.add(att);
		att = new Attribute("Radiant: Dragon Knight", pickedOrNot);
		heroMap.put("Radiant:DragonKnight", att);
		atts.add(att);
		att = new Attribute("Radiant: Drow Ranger", pickedOrNot);
		heroMap.put("Radiant:DrowRanger", att);
		atts.add(att);
		att = new Attribute("Radiant: Earth Spirit", pickedOrNot);
		heroMap.put("Radiant:EarthSpirit", att);
		atts.add(att);
		att = new Attribute("Radiant: Earthshaker", pickedOrNot);
		heroMap.put("Radiant:Earthshaker", att);
		atts.add(att);
		att = new Attribute("Radiant: Elder Titan", pickedOrNot);
		heroMap.put("Radiant:ElderTitan", att);
		atts.add(att);
		att = new Attribute("Radiant: Ember Spirit", pickedOrNot);
		heroMap.put("Radiant:EmberSpirit", att);
		atts.add(att);
		att = new Attribute("Radiant: Enchantress", pickedOrNot);
		heroMap.put("Radiant:Enchantress", att);
		atts.add(att);
		att = new Attribute("Radiant: Enigma", pickedOrNot);
		heroMap.put("Radiant:Enigma", att);
		atts.add(att);
		att = new Attribute("Radiant: Faceless Void", pickedOrNot);
		heroMap.put("Radiant:FacelessVoid", att);
		atts.add(att); 
		att = new Attribute("Radiant: Gyrocopter", pickedOrNot);
		heroMap.put("Radiant:Gyrocopter", att);
		atts.add(att); 
		att = new Attribute("Radiant: Huskar", pickedOrNot);
		heroMap.put("Radiant:Huskar", att);
		atts.add(att); 
		att = new Attribute("Radiant: Invoker", pickedOrNot);
		heroMap.put("Radiant:Invoker", att);
		atts.add(att); 
		att = new Attribute("Radiant: Io", pickedOrNot);
		heroMap.put("Radiant:Io", att);
		atts.add(att); 
		att = new Attribute("Radiant: Jakiro", pickedOrNot);
		heroMap.put("Radiant:Jakiro", att);
		atts.add(att); 
		att = new Attribute("Radiant: Juggernaut", pickedOrNot);
		heroMap.put("Radiant:Juggernaut", att);
		atts.add(att);
		att = new Attribute("Radiant: Keeper of the Light", pickedOrNot);
		heroMap.put("Radiant:KeeperoftheLight", att);
		atts.add(att);
		att = new Attribute("Radiant: Kunkka", pickedOrNot);
		heroMap.put("Radiant:Kunkka", att);
		atts.add(att); 
		att = new Attribute("Radiant: Legion Commander", pickedOrNot);
		heroMap.put("Radiant:LegionCommander", att);
		atts.add(att);
		att = new Attribute("Radiant: Leshrac", pickedOrNot);
		heroMap.put("Radiant:Leshrac", att);
		atts.add(att); 
		att = new Attribute("Radiant: Lich", pickedOrNot);
		heroMap.put("Radiant:Lich", att);
		atts.add(att);
		att = new Attribute("Radiant: Lifestealer", pickedOrNot);
		heroMap.put("Radiant:Lifestealer", att);
		atts.add(att);
		att = new Attribute("Radiant: Lina", pickedOrNot);
		heroMap.put("Radiant:Lina", att);
		atts.add(att); 
		att = new Attribute("Radiant: Lion", pickedOrNot);
		heroMap.put("Radiant:Lion", att);
		atts.add(att);
		att = new Attribute("Radiant: Lone Druid", pickedOrNot);
		heroMap.put("Radiant:LoneDruid", att);
		atts.add(att);
		att = new Attribute("Radiant: Luna", pickedOrNot);
		heroMap.put("Radiant:Luna", att);
		atts.add(att);
		att = new Attribute("Radiant: Lycan", pickedOrNot);
		heroMap.put("Radiant:Lycan", att);
		atts.add(att);
		att = new Attribute("Radiant: Magnus", pickedOrNot);
		heroMap.put("Radiant:Magnus", att);
		atts.add(att);
		att = new Attribute("Radiant: Medusa", pickedOrNot);
		heroMap.put("Radiant:Medusa", att);
		atts.add(att);
		att = new Attribute("Radiant: Meepo", pickedOrNot);
		heroMap.put("Radiant:Meepo", att);
		atts.add(att);
		att = new Attribute("Radiant: Mirana", pickedOrNot);
		heroMap.put("Radiant:Mirana", att);
		atts.add(att); 
		att = new Attribute("Radiant: Morphling", pickedOrNot);
		heroMap.put("Radiant:Morphling", att);
		atts.add(att);
		att = new Attribute("Radiant: Naga Siren", pickedOrNot);
		heroMap.put("Radiant:NagaSiren", att);
		atts.add(att);
		att = new Attribute("Radiant: Nature's Prophet", pickedOrNot);
		heroMap.put("Radiant:Nature'sProphet", att);
		atts.add(att);
		att = new Attribute("Radiant: Necrophos", pickedOrNot);
		heroMap.put("Radiant:Necrophos", att);
		atts.add(att);
		att = new Attribute("Radiant: Night Stalker", pickedOrNot);
		heroMap.put("Radiant:NightStalker", att);
		atts.add(att);
		att = new Attribute("Radiant: Nyx Assassin", pickedOrNot);
		heroMap.put("Radiant:NyxAssassin", att);
		atts.add(att);
		att = new Attribute("Radiant: Ogre Magi", pickedOrNot);
		heroMap.put("Radiant:OgreMagi", att);
		atts.add(att);
		att = new Attribute("Radiant: Omniknight", pickedOrNot);
		heroMap.put("Radiant:Omniknight", att);
		atts.add(att);
		att = new Attribute("Radiant: Oracle", pickedOrNot);
		heroMap.put("Radiant:Oracle", att);
		atts.add(att);
		att = new Attribute("Radiant: Outworld Devourer", pickedOrNot);
		heroMap.put("Radiant:OutworldDevourer", att);
		atts.add(att);
		att = new Attribute("Radiant: Phantom Assassin", pickedOrNot);
		heroMap.put("Radiant:PhantomAssassin", att);
		atts.add(att);
		att = new Attribute("Radiant: Phantom Lancer", pickedOrNot);
		heroMap.put("Radiant:PhantomLancer", att);
		atts.add(att);
		att = new Attribute("Radiant: Phoenix", pickedOrNot);
		heroMap.put("Radiant:Phoenix", att);
		atts.add(att);
		att = new Attribute("Radiant: Puck", pickedOrNot);
		heroMap.put("Radiant:Puck", att);
		atts.add(att);
		att = new Attribute("Radiant: Pudge", pickedOrNot);
		heroMap.put("Radiant:Pudge", att);
		atts.add(att); 
		att = new Attribute("Radiant: Pugna", pickedOrNot);
		heroMap.put("Radiant:Pugna", att);
		atts.add(att); 
		att = new Attribute("Radiant: Queen of Pain", pickedOrNot);
		heroMap.put("Radiant:QueenofPain", att);
		atts.add(att); 
		att = new Attribute("Radiant: Razor", pickedOrNot);
		heroMap.put("Radiant:Razor", att);
		atts.add(att); 
		att = new Attribute("Radiant: Riki", pickedOrNot);
		heroMap.put("Radiant:Riki", att);
		atts.add(att);
		att = new Attribute("Radiant: Rubick", pickedOrNot);
		heroMap.put("Radiant:Rubick", att);
		atts.add(att);
		att = new Attribute("Radiant: Sand King", pickedOrNot);
		heroMap.put("Radiant:SandKing", att);
		atts.add(att);
		att = new Attribute("Radiant: Shadow Demon", pickedOrNot);
		heroMap.put("Radiant:ShadowDemon", att);
		atts.add(att);
		att = new Attribute("Radiant: Shadow Fiend", pickedOrNot);
		heroMap.put("Radiant:ShadowFiend", att);
		atts.add(att);
		att = new Attribute("Radiant: Shadow Shaman", pickedOrNot);
		heroMap.put("Radiant:ShadowShaman", att);
		atts.add(att);
		att = new Attribute("Radiant: Silencer", pickedOrNot);
		heroMap.put("Radiant:Silencer", att);
		atts.add(att);
		att = new Attribute("Radiant: Skywrath Mage", pickedOrNot);
		heroMap.put("Radiant:SkywrathMage", att);
		atts.add(att);
		att = new Attribute("Radiant: Slardar", pickedOrNot);
		heroMap.put("Radiant:Slardar", att);
		atts.add(att);
		att = new Attribute("Radiant: Slark", pickedOrNot);
		heroMap.put("Radiant:Slark", att);
		atts.add(att);
		att = new Attribute("Radiant: Sniper", pickedOrNot);
		heroMap.put("Radiant:Sniper", att);
		atts.add(att);
		att = new Attribute("Radiant: Spectre", pickedOrNot);
		heroMap.put("Radiant:Spectre", att);
		atts.add(att);
		att = new Attribute("Radiant: Spirit Breaker", pickedOrNot);
		heroMap.put("Radiant:SpiritBreaker", att);
		atts.add(att);
		att = new Attribute("Radiant: Storm Spirit", pickedOrNot);
		heroMap.put("Radiant:StormSpirit", att);
		atts.add(att);
		att = new Attribute("Radiant: Sven", pickedOrNot);
		heroMap.put("Radiant:Sven", att);
		atts.add(att);
		att = new Attribute("Radiant: Techies", pickedOrNot);
		heroMap.put("Radiant:Techies", att);
		atts.add(att);
		att = new Attribute("Radiant: Templar Assassin", pickedOrNot);
		heroMap.put("Radiant:TemplarAssassin", att);
		atts.add(att); 
		att = new Attribute("Radiant: Terrorblade", pickedOrNot);
		heroMap.put("Radiant:Terrorblade", att);
		atts.add(att);
		att = new Attribute("Radiant: Tidehunter", pickedOrNot);
		heroMap.put("Radiant:Tidehunter", att);
		atts.add(att);
		att = new Attribute("Radiant: Timbersaw", pickedOrNot);
		heroMap.put("Radiant:Timbersaw", att);
		atts.add(att);
		att = new Attribute("Radiant: Tinker", pickedOrNot);
		heroMap.put("Radiant:Tinker", att);
		atts.add(att);
		att = new Attribute("Radiant: Tiny", pickedOrNot);
		heroMap.put("Radiant:Tiny", att);
		atts.add(att); 
		att = new Attribute("Radiant: Treant Protector", pickedOrNot);
		heroMap.put("Radiant:TreantProtector", att);
		atts.add(att);
		att = new Attribute("Radiant: Troll Warlord", pickedOrNot);
		heroMap.put("Radiant:TrollWarlord", att);
		atts.add(att);
		att = new Attribute("Radiant: Tusk", pickedOrNot);
		heroMap.put("Radiant:Tusk", att);
		atts.add(att); 
		att = new Attribute("Radiant: Undying", pickedOrNot);
		heroMap.put("Radiant:Undying", att);
		atts.add(att);
		att = new Attribute("Radiant: Ursa", pickedOrNot);
		heroMap.put("Radiant:Ursa", att);
		atts.add(att);
		att = new Attribute("Radiant: Vengeful Spirit", pickedOrNot);
		heroMap.put("Radiant:VengefulSpirit", att);
		atts.add(att); 
		att = new Attribute("Radiant: Venomancer", pickedOrNot);
		heroMap.put("Radiant:Venomancer", att);
		atts.add(att);
		att = new Attribute("Radiant: Viper", pickedOrNot);
		heroMap.put("Radiant:Viper", att);
		atts.add(att); 
		att = new Attribute("Radiant: Visage", pickedOrNot);
		heroMap.put("Radiant:Visage", att);
		atts.add(att);
		att = new Attribute("Radiant: Warlock", pickedOrNot);
		heroMap.put("Radiant:Warlock", att);
		atts.add(att); 
		att = new Attribute("Radiant: Weaver", pickedOrNot);
		heroMap.put("Radiant:Weaver", att);
		atts.add(att); 
		att = new Attribute("Radiant: Windranger", pickedOrNot);
		heroMap.put("Radiant:Windranger", att);
		atts.add(att); 
		att = new Attribute("Radiant: Winter Wyvern", pickedOrNot);
		heroMap.put("Radiant:WinterWyvern", att);
		atts.add(att); 
		att = new Attribute("Radiant: Witch Doctor", pickedOrNot);
		heroMap.put("Radiant:WitchDoctor", att);
		atts.add(att); 
		att = new Attribute("Radiant: Wraith King", pickedOrNot);
		heroMap.put("Radiant:WraithKing", att);
		atts.add(att); 
		att = new Attribute("Radiant: Zeus", pickedOrNot);
		heroMap.put("Radiant:Zeus", att);
		atts.add(att); 
		
		att = new Attribute("Dire: Abaddon", pickedOrNot);
		heroMap.put("Dire:Abaddon", att);
		atts.add(att);
		att = new Attribute("Dire: Abyssal Underlord", pickedOrNot);
		heroMap.put("Dire:AbyssalUnderlord", att);
		atts.add(att);
		att = new Attribute("Dire: Alchemist", pickedOrNot);
		heroMap.put("Dire:Alchemist", att);
		atts.add(att);
		att = new Attribute("Dire: Ancient Apparition", pickedOrNot);
		heroMap.put("Dire:AncientApparition", att);
		atts.add(att);
		att = new Attribute("Dire: Anti-Mage", pickedOrNot);
		heroMap.put("Dire:Anti-Mage", att);
		atts.add(att);
		att = new Attribute("Dire: Arc Warden", pickedOrNot);
		heroMap.put("Dire:ArcWarden", att);
		atts.add(att);
		att = new Attribute("Dire: Axe", pickedOrNot);
		heroMap.put("Dire:Axe", att);
		atts.add(att);
		att = new Attribute("Dire: Bane", pickedOrNot);
		heroMap.put("Dire:Bane", att);
		atts.add(att);
		att = new Attribute("Dire: Batrider", pickedOrNot);
		heroMap.put("Dire:Batrider", att);
		atts.add(att);
		att = new Attribute("Dire: Beastmaster", pickedOrNot);
		heroMap.put("Dire:Beastmaster", att);
		atts.add(att);
		att = new Attribute("Dire: Bloodseeker", pickedOrNot);
		heroMap.put("Dire:Bloodseeker", att);
		atts.add(att);
		att = new Attribute("Dire: Bounty Hunter", pickedOrNot);
		heroMap.put("Dire:BountyHunter", att);
		atts.add(att);
		att = new Attribute("Dire: Brewmaster", pickedOrNot);
		heroMap.put("Dire:Brewmaster", att);
		atts.add(att);
		att = new Attribute("Dire: Bristleback", pickedOrNot);
		heroMap.put("Dire:Bristleback", att);
		atts.add(att);
		att = new Attribute("Dire: Broodmother", pickedOrNot);
		heroMap.put("Dire:Broodmother", att);
		atts.add(att);
		att = new Attribute("Dire: Centaur Warrunner", pickedOrNot);
		heroMap.put("Dire:CentaurWarrunner", att);
		atts.add(att);
		att = new Attribute("Dire: Chaos Knight", pickedOrNot);
		heroMap.put("Dire:ChaosKnight", att);
		atts.add(att);
		att = new Attribute("Dire: Chen", pickedOrNot);
		heroMap.put("Dire:Chen", att);
		atts.add(att);
		att = new Attribute("Dire: Clinkz", pickedOrNot);
		heroMap.put("Dire:Clinkz", att);
		atts.add(att);
		att = new Attribute("Dire: Clockwerk", pickedOrNot);
		heroMap.put("Dire:Clockwerk", att);
		atts.add(att);
		att = new Attribute("Dire: Crystal Maiden", pickedOrNot);
		heroMap.put("Dire:CrystalMaiden", att);
		atts.add(att);
		att = new Attribute("Dire: Dark Seer", pickedOrNot);
		heroMap.put("Dire:DarkSeer", att);
		atts.add(att);
		att = new Attribute("Dire: Dazzle", pickedOrNot);
		heroMap.put("Dire:Dazzle", att);
		atts.add(att);
		att = new Attribute("Dire: Death Prophet", pickedOrNot);
		heroMap.put("Dire:DeathProphet", att);
		atts.add(att);
		att = new Attribute("Dire: Disruptor", pickedOrNot);
		heroMap.put("Dire:Disruptor", att);
		atts.add(att);
		att = new Attribute("Dire: Doom", pickedOrNot);
		heroMap.put("Dire:Doom", att);
		atts.add(att);
		att = new Attribute("Dire: Dragon Knight", pickedOrNot);
		heroMap.put("Dire:DragonKnight", att);
		atts.add(att);
		att = new Attribute("Dire: Drow Ranger", pickedOrNot);
		heroMap.put("Dire:DrowRanger", att);
		atts.add(att);
		att = new Attribute("Dire: Earth Spirit", pickedOrNot);
		heroMap.put("Dire:EarthSpirit", att);
		atts.add(att);
		att = new Attribute("Dire: Earthshaker", pickedOrNot);
		heroMap.put("Dire:Earthshaker", att);
		atts.add(att);
		att = new Attribute("Dire: Elder Titan", pickedOrNot);
		heroMap.put("Dire:ElderTitan", att);
		atts.add(att);
		att = new Attribute("Dire: Ember Spirit", pickedOrNot);
		heroMap.put("Dire:EmberSpirit", att);
		atts.add(att);
		att = new Attribute("Dire: Enchantress", pickedOrNot);
		heroMap.put("Dire:Enchantress", att);
		atts.add(att);
		att = new Attribute("Dire: Enigma", pickedOrNot);
		heroMap.put("Dire:Enigma", att);
		atts.add(att);
		att = new Attribute("Dire: Faceless Void", pickedOrNot);
		heroMap.put("Dire:FacelessVoid", att);
		atts.add(att); 
		att = new Attribute("Dire: Gyrocopter", pickedOrNot);
		heroMap.put("Dire:Gyrocopter", att);
		atts.add(att); 
		att = new Attribute("Dire: Huskar", pickedOrNot);
		heroMap.put("Dire:Huskar", att);
		atts.add(att); 
		att = new Attribute("Dire: Invoker", pickedOrNot);
		heroMap.put("Dire:Invoker", att);
		atts.add(att); 
		att = new Attribute("Dire: Io", pickedOrNot);
		heroMap.put("Dire:Io", att);
		atts.add(att); 
		att = new Attribute("Dire: Jakiro", pickedOrNot);
		heroMap.put("Dire:Jakiro", att);
		atts.add(att); 
		att = new Attribute("Dire: Juggernaut", pickedOrNot);
		heroMap.put("Dire:Juggernaut", att);
		atts.add(att);
		att = new Attribute("Dire: Keeper of the Light", pickedOrNot);
		heroMap.put("Dire:KeeperoftheLight", att);
		atts.add(att);
		att = new Attribute("Dire: Kunkka", pickedOrNot);
		heroMap.put("Dire:Kunkka", att);
		atts.add(att); 
		att = new Attribute("Dire: Legion Commander", pickedOrNot);
		heroMap.put("Dire:LegionCommander", att);
		atts.add(att);
		att = new Attribute("Dire: Leshrac", pickedOrNot);
		heroMap.put("Dire:Leshrac", att);
		atts.add(att); 
		att = new Attribute("Dire: Lich", pickedOrNot);
		heroMap.put("Dire:Lich", att);
		atts.add(att);
		att = new Attribute("Dire: Lifestealer", pickedOrNot);
		heroMap.put("Dire:Lifestealer", att);
		atts.add(att);
		att = new Attribute("Dire: Lina", pickedOrNot);
		heroMap.put("Dire:Lina", att);
		atts.add(att); 
		att = new Attribute("Dire: Lion", pickedOrNot);
		heroMap.put("Dire:Lion", att);
		atts.add(att);
		att = new Attribute("Dire: Lone Druid", pickedOrNot);
		heroMap.put("Dire:LoneDruid", att);
		atts.add(att);
		att = new Attribute("Dire: Luna", pickedOrNot);
		heroMap.put("Dire:Luna", att);
		atts.add(att);
		att = new Attribute("Dire: Lycan", pickedOrNot);
		heroMap.put("Dire:Lycan", att);
		atts.add(att);
		att = new Attribute("Dire: Magnus", pickedOrNot);
		heroMap.put("Dire:Magnus", att);
		atts.add(att);
		att = new Attribute("Dire: Medusa", pickedOrNot);
		heroMap.put("Dire:Medusa", att);
		atts.add(att);
		att = new Attribute("Dire: Meepo", pickedOrNot);
		heroMap.put("Dire:Meepo", att);
		atts.add(att);
		att = new Attribute("Dire: Mirana", pickedOrNot);
		heroMap.put("Dire:Mirana", att);
		atts.add(att); 
		att = new Attribute("Dire: Morphling", pickedOrNot);
		heroMap.put("Dire:Morphling", att);
		atts.add(att);
		att = new Attribute("Dire: Naga Siren", pickedOrNot);
		heroMap.put("Dire:NagaSiren", att);
		atts.add(att);
		att = new Attribute("Dire: Nature's Prophet", pickedOrNot);
		heroMap.put("Dire:Nature'sProphet", att);
		atts.add(att);
		att = new Attribute("Dire: Necrophos", pickedOrNot);
		heroMap.put("Dire:Necrophos", att);
		atts.add(att);
		att = new Attribute("Dire: Night Stalker", pickedOrNot);
		heroMap.put("Dire:NightStalker", att);
		atts.add(att);
		att = new Attribute("Dire: Nyx Assassin", pickedOrNot);
		heroMap.put("Dire:NyxAssassin", att);
		atts.add(att);
		att = new Attribute("Dire: Ogre Magi", pickedOrNot);
		heroMap.put("Dire:OgreMagi", att);
		atts.add(att);
		att = new Attribute("Dire: Omniknight", pickedOrNot);
		heroMap.put("Dire:Omniknight", att);
		atts.add(att);
		att = new Attribute("Dire: Oracle", pickedOrNot);
		heroMap.put("Dire:Oracle", att);
		atts.add(att);
		att = new Attribute("Dire: Outworld Devourer", pickedOrNot);
		heroMap.put("Dire:OutworldDevourer", att);
		atts.add(att);
		att = new Attribute("Dire: Phantom Assassin", pickedOrNot);
		heroMap.put("Dire:PhantomAssassin", att);
		atts.add(att);
		att = new Attribute("Dire: Phantom Lancer", pickedOrNot);
		heroMap.put("Dire:PhantomLancer", att);
		atts.add(att);
		att = new Attribute("Dire: Phoenix", pickedOrNot);
		heroMap.put("Dire:Phoenix", att);
		atts.add(att);
		att = new Attribute("Dire: Puck", pickedOrNot);
		heroMap.put("Dire:Puck", att);
		atts.add(att);
		att = new Attribute("Dire: Pudge", pickedOrNot);
		heroMap.put("Dire:Pudge", att);
		atts.add(att); 
		att = new Attribute("Dire: Pugna", pickedOrNot);
		heroMap.put("Dire:Pugna", att);
		atts.add(att); 
		att = new Attribute("Dire: Queen of Pain", pickedOrNot);
		heroMap.put("Dire:QueenofPain", att);
		atts.add(att); 
		att = new Attribute("Dire: Razor", pickedOrNot);
		heroMap.put("Dire:Razor", att);
		atts.add(att); 
		att = new Attribute("Dire: Riki", pickedOrNot);
		heroMap.put("Dire:Riki", att);
		atts.add(att);
		att = new Attribute("Dire: Rubick", pickedOrNot);
		heroMap.put("Dire:Rubick", att);
		atts.add(att);
		att = new Attribute("Dire: Sand King", pickedOrNot);
		heroMap.put("Dire:SandKing", att);
		atts.add(att);
		att = new Attribute("Dire: Shadow Demon", pickedOrNot);
		heroMap.put("Dire:ShadowDemon", att);
		atts.add(att);
		att = new Attribute("Dire: Shadow Fiend", pickedOrNot);
		heroMap.put("Dire:ShadowFiend", att);
		atts.add(att);
		att = new Attribute("Dire: Shadow Shaman", pickedOrNot);
		heroMap.put("Dire:ShadowShaman", att);
		atts.add(att);
		att = new Attribute("Dire: Silencer", pickedOrNot);
		heroMap.put("Dire:Silencer", att);
		atts.add(att);
		att = new Attribute("Dire: Skywrath Mage", pickedOrNot);
		heroMap.put("Dire:SkywrathMage", att);
		atts.add(att);
		att = new Attribute("Dire: Slardar", pickedOrNot);
		heroMap.put("Dire:Slardar", att);
		atts.add(att);
		att = new Attribute("Dire: Slark", pickedOrNot);
		heroMap.put("Dire:Slark", att);
		atts.add(att);
		att = new Attribute("Dire: Sniper", pickedOrNot);
		heroMap.put("Dire:Sniper", att);
		atts.add(att);
		att = new Attribute("Dire: Spectre", pickedOrNot);
		heroMap.put("Dire:Spectre", att);
		atts.add(att);
		att = new Attribute("Dire: Spirit Breaker", pickedOrNot);
		heroMap.put("Dire:SpiritBreaker", att);
		atts.add(att);
		att = new Attribute("Dire: Storm Spirit", pickedOrNot);
		heroMap.put("Dire:StormSpirit", att);
		atts.add(att);
		att = new Attribute("Dire: Sven", pickedOrNot);
		heroMap.put("Dire:Sven", att);
		atts.add(att);
		att = new Attribute("Dire: Techies", pickedOrNot);
		heroMap.put("Dire:Techies", att);
		atts.add(att);
		att = new Attribute("Dire: Templar Assassin", pickedOrNot);
		heroMap.put("Dire:TemplarAssassin", att);
		atts.add(att); 
		att = new Attribute("Dire: Terrorblade", pickedOrNot);
		heroMap.put("Dire:Terrorblade", att);
		atts.add(att);
		att = new Attribute("Dire: Tidehunter", pickedOrNot);
		heroMap.put("Dire:Tidehunter", att);
		atts.add(att);
		att = new Attribute("Dire: Timbersaw", pickedOrNot);
		heroMap.put("Dire:Timbersaw", att);
		atts.add(att);
		att = new Attribute("Dire: Tinker", pickedOrNot);
		heroMap.put("Dire:Tinker", att);
		atts.add(att);
		att = new Attribute("Dire: Tiny", pickedOrNot);
		heroMap.put("Dire:Tiny", att);
		atts.add(att); 
		att = new Attribute("Dire: Treant Protector", pickedOrNot);
		heroMap.put("Dire:TreantProtector", att);
		atts.add(att);
		att = new Attribute("Dire: Troll Warlord", pickedOrNot);
		heroMap.put("Dire:TrollWarlord", att);
		atts.add(att);
		att = new Attribute("Dire: Tusk", pickedOrNot);
		heroMap.put("Dire:Tusk", att);
		atts.add(att); 
		att = new Attribute("Dire: Undying", pickedOrNot);
		heroMap.put("Dire:Undying", att);
		atts.add(att);
		att = new Attribute("Dire: Ursa", pickedOrNot);
		heroMap.put("Dire:Ursa", att);
		atts.add(att);
		att = new Attribute("Dire: Vengeful Spirit", pickedOrNot);
		heroMap.put("Dire:VengefulSpirit", att);
		atts.add(att); 
		att = new Attribute("Dire: Venomancer", pickedOrNot);
		heroMap.put("Dire:Venomancer", att);
		atts.add(att);
		att = new Attribute("Dire: Viper", pickedOrNot);
		heroMap.put("Dire:Viper", att);
		atts.add(att); 
		att = new Attribute("Dire: Visage", pickedOrNot);
		heroMap.put("Dire:Visage", att);
		atts.add(att);
		att = new Attribute("Dire: Warlock", pickedOrNot);
		heroMap.put("Dire:Warlock", att);
		atts.add(att); 
		att = new Attribute("Dire: Weaver", pickedOrNot);
		heroMap.put("Dire:Weaver", att);
		atts.add(att); 
		att = new Attribute("Dire: Windranger", pickedOrNot);
		heroMap.put("Dire:Windranger", att);
		atts.add(att); 
		att = new Attribute("Dire: Winter Wyvern", pickedOrNot);
		heroMap.put("Dire:WinterWyvern", att);
		atts.add(att); 
		att = new Attribute("Dire: Witch Doctor", pickedOrNot);
		heroMap.put("Dire:WitchDoctor", att);
		atts.add(att); 
		att = new Attribute("Dire: Wraith King", pickedOrNot);
		heroMap.put("Dire:WraithKing", att);
		atts.add(att); 
		att = new Attribute("Dire: Zeus", pickedOrNot);
		heroMap.put("Dire:Zeus", att);
		atts.add(att); 

		att = new Attribute("Victory", Arrays.asList("Radiant","Dire"));
		heroMap.put("Victory", att);
		atts.add(att);
		
		return atts;
	}
	
	private BufferedReader readDataFile(String filename) {
        BufferedReader inputReader = null;
        try {
            inputReader = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException ex) {
            System.err.println("File not found: " + filename);
        }
        return inputReader;
    }

	public static void main(String [] args){
		Refresh re = new Refresh();
		re.refreshFiles();
	}
}

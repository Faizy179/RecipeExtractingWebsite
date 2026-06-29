package recipeextractor;
import java.time.Duration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
public class recipeExtractor {
    final static int MAX_RETRIES = 3;

    public static Document scrape(String url){
        
        // ==========================================
        // TRY 1: The "Dumb" Fast Scrape (Jsoup)
        // ==========================================
        try {
            System.out.println("Attempting fast Jsoup scrape...");
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(5000) // NEW: Give up after 5 seconds so the frontend doesn't timeout!
                .ignoreHttpErrors(true) // NEW: Don't crash on Cloudflare 403/503 errors!
                .get();
            
            // If we bypassed the firewall, return the document immediately!
            if (!doc.title().contains("Just a moment") && !doc.title().contains("Attention Required") && !doc.title().contains("Security")) {
                
                // NEW: Check if the raw HTML actually contains the JSON-LD or Microdata!
                if (!doc.select("script[type='application/ld+json'], [itemtype*='schema.org/Recipe']").isEmpty()) {
                    System.out.println("Jsoup scrape successful AND found recipe data!");
                    return doc;
                } else {
                    System.out.println("Jsoup bypassed the firewall, but the page requires JavaScript to load the recipe. Falling back to Selenium...");
                }
            }
            System.out.println("Jsoup hit a firewall. Falling back to Selenium...");
        } catch (Exception e) {
            System.out.println("Jsoup fast-scrape failed. Falling back to Selenium...");
        }

        // ==========================================
        // TRY 2: The Stealth Headless Scrape (Selenium)
        // ==========================================
        String htmlContent;
        ChromeOptions options = new ChromeOptions();
        
        // 1. MUST be headless so your Nest server doesn't crash!
        options.addArguments("--headless=new"); 
        
        // 2. The Disguise: Make the invisible browser look like a real monitor
        options.addArguments("--window-size=1920,1080"); 
        options.addArguments("--start-maximized");
        
        // 3. Standard anti-bot and stability flags
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--lang=en-US");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
       WebDriver driver = null;
        try{
            driver = new ChromeDriver(options);
            
            // 1. The Stealth Command (Wipe the Bot Flag)
            ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                java.util.Map.of("source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"));

            // ==========================================
            // 🌟 NEW: THE GEO-SPOOFING COMMANDS
            // ==========================================
            
            // Enable Network modifications
            ((ChromeDriver) driver).executeCdpCommand("Network.enable", new java.util.HashMap<>());
            
            // Inject a US IP Address into the HTTP Headers to bypass the UK Redirect
            java.util.Map<String, Object> headers = new java.util.HashMap<>();
            headers.put("X-Forwarded-For", "8.8.8.8"); // A US-based IP
            headers.put("Accept-Language", "en-US,en;q=0.9");
            
            java.util.Map<String, Object> networkParams = new java.util.HashMap<>();
            networkParams.put("headers", headers);
            ((ChromeDriver) driver).executeCdpCommand("Network.setExtraHTTPHeaders", networkParams);
            
            // Override the Browser's GPS Coordinates to New York City
            java.util.Map<String, Object> geoParams = new java.util.HashMap<>();
            geoParams.put("latitude", 40.7128);
            geoParams.put("longitude", -74.0060);
            geoParams.put("accuracy", 100);
            ((ChromeDriver) driver).executeCdpCommand("Emulation.setGeolocationOverride", geoParams);
            
            // ==========================================

            // NOW we navigate to the URL, disguised as a New Yorker!
            driver.get(url);
            
            System.out.println("Final Landing URL: " + driver.getCurrentUrl()); // <-- Let's print this to verify!
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            try{
                wait.until(d -> !d.findElements(By.cssSelector("script[type='application/ld+json'], [itemtype*='schema.org/Recipe']")).isEmpty());
            } catch(TimeoutException a) {
                System.out.println("No JSON-LD or Microdata found after waiting");
            }

            htmlContent = driver.getPageSource();
            return Jsoup.parse(htmlContent);

        } catch(Exception e) {
            System.out.println("Selenium Scraping failed");
            e.printStackTrace();
            return null;
        } finally {
            if(driver != null) {
                driver.quit(); // Crucial to prevent RAM exhaustion on the Nest server!
            }
        }
    }   
    public static JsonObject extractRecipe(Document document){

        Elements jsonElements = document.select("script[type=application/ld+json]");

         System.out.println("Found " + jsonElements.size() + " JSON-LD scripts");
        for (Element jsonElement : jsonElements){
            String rawJson = jsonElement.data().trim();
            try{
                JsonElement element = JsonParser.parseString(rawJson);
                if(element.isJsonObject()){
                    JsonObject result = processJsonObject(element.getAsJsonObject());
                    if(result != null){
                        return result;
                    }
                }
                else if(element.isJsonArray()){
                    JsonArray array = element.getAsJsonArray();
                    final int length = array.size();
                    for(int i = 0; i < length; i++){
                        if (array.get(i).isJsonObject()) {
                            JsonObject result = processJsonObject(array.get(i).getAsJsonObject());
                            if (result != null){
                                return result;
                            }
                        }
                    }
                }
            }
            catch(Exception e){
                System.out.println("ERROR PARSING JSON: " + e.getMessage());
            }
        }
        return extractMicrodata(document);
    }
    public static JsonObject extractMicrodata(Document document){
        Element recipeElement = document.selectFirst("[itemtype*=http://schema.org/Recipe]");
        JsonObject data = new JsonObject();
        JsonArray ingredients = new JsonArray();
        JsonArray instructions = new JsonArray();
        String listSelectors = "[itemprop=recipeInstructions] li, [itemprop=recipeInstruction] li, [itemprop=instructions] li";
        String containerSelectors = "[itemprop=recipeInstructions], [itemprop=recipeInstruction], [itemprop=instructions]";
        if(recipeElement == null){
            System.out.println("Recipe was returned as null");
            return null;
        }
        System.out.println("Recipe was not returned as null");
        Element nameElement = recipeElement.selectFirst("[itemprop=name]");
       
        Elements ingredientElements = recipeElement.select("[itemprop=recipeIngredient], [itemprop=ingredients]");
        for (Element element : ingredientElements){
            ingredients.add(element.text());
        }
        Elements instructionElements = recipeElement.select(listSelectors);
        if(instructionElements.isEmpty()){
            instructionElements = recipeElement.select(containerSelectors);
        }
        
        for(Element element : instructionElements){
            instructions.add(element.text());
        }

        if (instructions.isEmpty()) {
            
            Elements allElements = recipeElement.select("h2, h3, h4, p, li");
            
            if (allElements.isEmpty()) {
                allElements = document.select("h2, h3, h4, p, li"); 
            }
            
            boolean inInstructions = false;
            
            for (Element el : allElements) {
                String tagName = el.tagName();
                String text = el.text().toLowerCase();
                
                if (tagName.matches("h2|h3|h4") && (text.contains("instructions") || text.contains("directions") || text.contains("method"))) {
                    inInstructions = true;
                    continue; 
                }
                
                if (inInstructions) {
                    if (tagName.matches("h2|h3|h4")) {
                        break;
                    }
                    
                    if ((tagName.equals("p") || tagName.equals("li")) && !el.text().trim().isEmpty()) {
                        if(!el.hasAttr("itemprop")) { 
                            instructions.add(el.text());
                        }
                    }
                }
            }
        }
        if(nameElement == null){
            data.addProperty("name",document.title());
        }
        else{
            data.addProperty("name",nameElement.text());
        }
        data.add("ingredients",ingredients);
        data.add("instructions",instructions);
        System.out.println(document.title());
        for(JsonElement element : ingredients){
            System.out.println(element.getAsString());
        }
        for(JsonElement element : instructions){
            System.out.println(element.getAsString());
        }
        return data;
    }
    public static JsonArray extractIngredients(JsonObject recipe){
        JsonArray ingredientsList = new JsonArray();

        if(recipe.has("recipeIngredient")){
            JsonElement element = recipe.get("recipeIngredient");
            if(element.isJsonArray()){
                JsonArray ingredients = recipe.getAsJsonArray("recipeIngredient");
                final int size = ingredients.size();
                for( int i =0; i < size; i++){
                    ingredientsList.add(ingredients.get(i).getAsString());
                }  
            }
            else if(element.isJsonPrimitive()){
                ingredientsList.add(element.getAsString());
            }
           
        }
        return ingredientsList;
    }//ectracts the ingredients in the recipe and returns it as a json array

    public static JsonArray extractInstructions(JsonObject recipe){
        JsonArray instructionsList = new JsonArray();
        if(recipe.has("recipeInstructions")){
            JsonElement instructionElement = recipe.get("recipeInstructions");
            if(instructionElement.isJsonArray()){
                JsonArray instructions = instructionElement.getAsJsonArray();
                final int length = instructions.size();
                for(int i = 0; i < length; i++){
                    JsonElement element = instructions.get(i);
                    if(element.isJsonObject()){
                        JsonObject obj = element.getAsJsonObject();
                        if(obj.has("text")){
                            instructionsList.add(obj.get("text").getAsString());
                        }
                    }
                    else if (element.isJsonPrimitive()){
                        instructionsList.add(element.getAsString());
                    }
                }
            }
            else if(instructionElement.isJsonObject()){
                JsonObject obj = instructionElement.getAsJsonObject();
                if(obj.has("text")){
                    instructionsList.add(obj.get("text").getAsString());
                }
            }
            else if(instructionElement.isJsonPrimitive()){
                instructionsList.add(instructionElement.getAsString());
            }
        }
        return instructionsList;
    }//returns a json array of the recipe instructions
    public static JsonObject processJsonObject(JsonObject object){
        if(object.has("@type")){
            if(checkIfRecipeType(object.get("@type"))){
                return buildFrontendPayload(object);
            }
        }
        if(object.has("@graph")){
            JsonElement element = object.get("@graph");
            if(element.isJsonArray()){
                JsonArray array = element.getAsJsonArray();
                final int length = array.size();
                for(int i = 0; i < length; i++){
                    JsonElement arrayElement = array.get(i);
                    if(arrayElement.isJsonObject()){
                        JsonObject item = arrayElement.getAsJsonObject();
                        if(item.has("@type")){
                            if(checkIfRecipeType(item.get("@type"))){
                                return buildFrontendPayload(item);
                            }
                        }
                    }
                }
            }      
        }
        return null;
    }
    public static boolean checkIfRecipeType(JsonElement element){
        if(element.isJsonPrimitive()){
            if(element.getAsString().equals("Recipe")){
                return true;
            }

        }
        else if(element.isJsonArray()){
            JsonArray array = element.getAsJsonArray();
            final int size = array.size();
            for (int i = 0; i < size; i++){
                if(array.get(i).getAsString().equals("Recipe")){
                    return true;
                }
            }
        }
        return false;
    }
    public static JsonObject buildFrontendPayload(JsonObject object){
        JsonObject data = new JsonObject();
        String recipeName = "N/A";
        if(object.has("name")){
            recipeName = object.get("name").getAsString();
        }
        data.addProperty("name", recipeName);
        data.add("ingredients", extractIngredients(object));
        data.add("instructions", extractInstructions(object));
        return data;
    }
}
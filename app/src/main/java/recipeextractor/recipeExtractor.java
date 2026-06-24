package recipeextractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.io.IOException;
public class recipeExtractor {
    final static int MAX_RETRIES = 3;
    public static void main(String[] args) {
        String url = "https://joyfoodsunshine.com/the-most-amazing-chocolate-chip-cookies/";

        Document document = scrape(url);

        if(document != null){                                              
            JsonObject data = extractRecipe(document);
            if(data != null){
                System.out.println(data);//print the toString of the data object
            }
            else{
                System.out.println("Could not find recipe data on page");
            }
        }
        else{
            System.err.println("Failed to extract recipe from  " + url);
        }
    }
    public static Document scrape(String url){
        String htmlContent;
       ChromeOptions options = new ChromeOptions();
       options.addArguments("--headless=new");
       options.addArguments("--disable-blink-features=AutomationControlled");
       options.addArguments("--no-sandbox");
       options.addArguments("--disable-dev-shm-usage");
       options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

       WebDriver driver = null;
       try{
        driver = new ChromeDriver(options);
        driver.get(url);//navigqates to that specific recipe website
        htmlContent = driver.getPageSource();

        return Jsoup.parse(htmlContent);

       }
       catch(Exception e){
        System.out.println("Scraping failed");
        return null;
       } 
       finally{
        if(driver != null){
            driver.quit();
        }
       }
    }   
    public static JsonObject extractRecipe(Document document){

        Elements jsonElements = document.select("script[type=application/ld+json]");
        for (Element jsonElement : jsonElements){
            String rawJson = jsonElement.html().trim();
            try{
                JsonElement parsed = JsonParser.parseString(rawJson);
                if(JsonParser.parseString(rawJson).isJsonObject()){
                    JsonObject result = processJsonObject(JsonParser.parseString(rawJson).getAsJsonObject());
                    if(result != null){
                        return result;
                    }
                }
                else if(parsed.isJsonArray()){
                    JsonArray array = parsed.getAsJsonArray();
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

            }
        }
        return null;
    }
    public static JsonArray extractIngredients(JsonObject recipe){
        JsonArray ingredientsList = new JsonArray();

        if(recipe.has("recipeIngredient")){
            JsonArray ingredients = recipe.getAsJsonArray("recipeIngredient");
            final int size = ingredients.size();
            for( int i =0; i < size; i++){
                ingredientsList.add(ingredients.get(i).getAsString());
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
            JsonArray graphArray = object.getAsJsonArray("@graph");
            final int length = graphArray.size();
            for(int i =0; i < length; i++){
                JsonObject item = graphArray.get(i).getAsJsonObject();
                if (item.has("@type")) {
                    if (checkIfRecipeType(item.get("@type"))) {
                        return buildFrontendPayload(item);

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
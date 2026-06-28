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
        String htmlContent;
       ChromeOptions options = new ChromeOptions();
       options.addArguments("--headless=new");
       options.addArguments("--disable-blink-features=AutomationControlled");
       options.addArguments("--no-sandbox");
       options.addArguments("--disable-dev-shm-usage");
       options.addArguments("--lang=en-US");
       options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
          WebDriver driver = null;
       try{
        driver = new ChromeDriver(options);
        driver.get(url);//navigqates to that specific recipe website
        System.out.println("Page Title: " + driver.getTitle());
        System.out.println("Page URL: " + driver.getCurrentUrl());
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try{
            wait.until(d -> !d.findElements(By.cssSelector("script[type = 'application/ld+json']")).isEmpty());
        }
        catch(TimeoutException a){
            System.out.println("No JSON-LD found after waiting");
        }
        

        htmlContent = driver.getPageSource();
        int index = htmlContent.indexOf("recipeIngredient");

        if (index != -1) {
            int start = Math.max(0, index - 500);
            int end = Math.min(htmlContent.length(), index + 1500);
            System.out.println(htmlContent.substring(start, end));
        }
        return Jsoup.parse(htmlContent);

       }
       catch(Exception e){
        System.out.println("Scraping failed");
        e.printStackTrace();
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
        JsonObject data = new JsonObject();
        String recipeName = document.title();
        JsonArray ingredients = new JsonArray();
        JsonArray instructions = new JsonArray();
        for (Element ingredient : document.select("[itemprop=recipeIngredient]")){
            ingredients.add(ingredient.text());
        }
        if(ingredients.isEmpty()){
            return null;
        }
        for(Element instruction : document.select("[itemprop=recipeInstructions]")){
            instructions.add(instruction.text());
        }
        data.addProperty("name", recipeName);
        data.add("ingredients",ingredients);
        data.add("instructions",instructions);
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
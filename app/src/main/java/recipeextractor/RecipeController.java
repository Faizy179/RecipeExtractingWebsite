package recipeextractor;
import org.springframework.web.bind.annotation.*;
import com.google.gson.JsonObject;
import org.jsoup.nodes.Document;
    @RestController
    @RequestMapping("/api")
    @CrossOrigin(origins = "*")
public class RecipeController {
    @GetMapping ("/extract")
    public String getRecipe(@RequestParam("url") String url){
        Document document= recipeExtractor.scrape(url);
        if(document != null){
            JsonObject data = recipeExtractor.extractRecipe(document);
            if (data != null) {
                return data.toString(); 
            }
        }
        return "{\"error\": \"Could not extract recipe data from the provided link.\"}";
    }
}

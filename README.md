# Declutter Any Recipe | Recipe Extracting Website
## What Was I Trying to Solve?
Anyone who has looke dup a recipe online, trying to find the perfect recipe knows the feeling of going through a dozen websites, trying to filter through the useless story. Well no more. With this website you can enter any* recipe and get it de-cluttered

## 3 Major Quality Of Life Improvements
1. **Declutters most online recipies, leaving you with just the recipe
2. **By simply checking a box you can stop your phone from sleeping while using the site
3. **Lastly, the instructions act as interactive elements, where when you click on them, you can grey them out to mark that step as complete

## Tech Stack
* **Frontend:** HTML, CSS, and JavaScript
* **Backend:** Java Spring Boot REST API
* **Web Scrapping:** Selenium WebDriver and Jsoup for custom DOM parsing
* **Data Extraction and Logic:** Uses Google Gson to parse  nested JSON-LD data. The backend includes custom logic to safely navigate both arrays and single objects within the `@graph` payload, ensuring the server does not crash on edge cases.

## Restrictions
This code only works on some websites. A significant portion of websites that may include anti scraping measures restrict the usage of this website. Furtheremore, since the java project is running on a nest server, some websites may load with a redirection pop up resulting in the crashing of the code.

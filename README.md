# URL Opener Selenium (Maven Project)

This project starts a small HTTP server with a web page to accept a URL and opens it in a **new browser window** using **Selenium WebDriver (Chrome)**.

## Requirements

- Java 11+ (you can lower to 8 by changing `pom.xml` if needed)
- Maven
- Google Chrome installed
- Internet access (first run downloads the matching ChromeDriver via WebDriverManager)

## How to Run

From the project root:

```bash
mvn clean compile
mvn exec:java
```

This uses the `exec-maven-plugin` and runs `com.example.UrlOpenerServer`.

The server will start on:

- http://localhost:4567

## Usage

1. Open http://localhost:4567 in your browser.
2. Enter a URL like `google.com` or `https://github.com`.
3. Click **Open in New Browser Window**.
4. A *new Chrome browser window* will launch and open the given URL.

Each submission creates a new WebDriver instance → a new browser window.

## Notes

- Browser windows are not closed automatically so you can interact with them.
- To close them programmatically, you could keep references to the `WebDriver` instances and call `quit()`.

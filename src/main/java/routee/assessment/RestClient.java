package routee.assessment;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestClient {

    private static final String TOKEN_URL = "https://auth.routee.net/oauth/token";
    private static final String PUSH_URL = "https://connect.routee.net/sms";
    private static final String APP_ID = "5f9138288b71de3617a87cd3";
    private static final String SECRET = "RSj69jLowJ";
    private static final String PHONE_NUMBER = "+306978745957";
    private static final String WEATHER_URL =
            "http://api.openweathermap.org/data/2.5/weather?q=%s&units=metric&mode=xml&appid=%s";
    private static final String WEATHER_API_KEY = "4e79189ec099e884c480a938f0b32674";
    private static final String WEATHER_CITY = "Thessaloniki";
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private static final RestClient instance = new RestClient();
    private String authToken;

    private RestClient() {
    }

    public static String getPhoneNumber() {
        return PHONE_NUMBER;
    }

    public static RestClient getInstance() {
        return instance;
    }

    /**
     * Retrieves an access token from Routee Api and saves it in {@link routee.assessment.RestClient#authToken}
     *
     * @return Boolean : true if succeeded, false if response status is not OK (200), or failed to get token
     * value form response, null if exception was raised
     */
    public Boolean authenticateRouteeApiUser() {
        try {
            String creds = APP_ID + ":" + SECRET;
            String credsBase64 = Base64.getEncoder().encodeToString(creds.getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(TOKEN_URL))
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .header("authorization", String.format("Basic %s", credsBase64))
                    .header("content-type", "application/x-www-form-urlencoded")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Boolean.FALSE;
            }

            // Quick and dirty way to retrieve token from json pair
            // ("access_token":"xxxx-xxxx-xxxx-xxx-xxx")
            Matcher m = Pattern.compile("\"access_token\":\"([a-zA-Z\\d-]*)\"")
                    .matcher(response.body());
            if (m.find()) {
                authToken = m.group(1);
                return authToken != null ? Boolean.TRUE : Boolean.FALSE;
            }

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Sends appropriate SMS to {@link routee.assessment.RestClient#PHONE_NUMBER} via Routee Api
     * In case of unauthorized request (response status 401/402) tries to reissue a token and resend (once)
     *
     * @param temperature
     * @return Optional: true if api response = 200, false if api response = 401 or 402, null if exception was raised
     */
    public Optional<Boolean> pushNotification(float temperature) {
        try {
            String msg = String.format("Your name and Temperature %s than 20C. %.2f", (temperature > 20f ? "more" : "less"), temperature);

            final String body = "{ \"body\": \"%s\",\"to\" : \"%s\",\"from\": \"amdTelecom\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(PUSH_URL))
                    .POST(HttpRequest.BodyPublishers.ofString(String.format(body, msg, PHONE_NUMBER)))
                    .header("authorization", String.format("Bearer %s", authToken))
                    .header("content-type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Unauthorized request, reissue token and try again
            if (response.statusCode() == 401
                    || response.statusCode() == 403) {
                if (!authenticateRouteeApiUser().equals(Boolean.TRUE)) {
                    return Optional.of(false);
                }
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() != 200) {
                return Optional.of(false);
            }

            return Optional.of(true);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * Retrieves the temperature for @link {{@link routee.assessment.RestClient#WEATHER_CITY}}
     *
     * @return
     */
    public Optional<Float> getWeatherData() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(String.format(WEATHER_URL, WEATHER_CITY, WEATHER_API_KEY)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(findTemperature(response.body()));

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * Retrieves for data (XML) the /current/temperature XPath
     *
     * @param data
     * @return
     * @throws Exception
     */
    private Float findTemperature(String data)
            throws Exception {
        if (data == null) {
            throw new IllegalArgumentException();
        }
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        InputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        Document document = builder.parse(is);
        XPath xPath = XPathFactory.newInstance().newXPath();
        String expression = "/current/temperature";
        Node node = (Node) xPath.compile(expression).evaluate(document, XPathConstants.NODE);
        String res = node.getAttributes().getNamedItem("value").getNodeValue();
        return Float.valueOf(res);
    }

}

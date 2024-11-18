import com.slack.api.Slack;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.PlainTextInputElement;
import com.slack.api.model.view.View;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.view.ViewClose;
import com.slack.api.model.view.ViewSubmit;
import com.slack.api.model.view.ViewTitle;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SlackFormApp {
    private static final String SLACK_BOT_TOKEN = "xoxb-8064078142368-8041315279506-mTDJPDYUDmd7Iry4t7TiliM1";
    private static final Slack slack = Slack.getInstance();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/slack/commands", handleCommands());
        server.createContext("/slack/interactions", handleInteractions());
        server.setExecutor(null); // Default executor
        server.start();
        System.out.println("Server is listening on port 8080");
    }

    private static HttpHandler handleCommands() {
        return exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                String triggerId = parseFormBody(body, "trigger_id");
                if (!triggerId.isEmpty()) {
                    openModal(triggerId);
                }

                // Acknowledge the command without sending any visible message
                String response = "";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        };
    }

    private static HttpHandler handleInteractions() {
        return exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Map<String, String> formData = parseFormData(body);

                // Handle form submission data
                System.out.println("Form Submission Data:");
                formData.forEach((key, value) -> System.out.println(key + ": " + value));

                // Respond to Slack that the form was received
                String response = "Form received";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        };
    }

    private static void openModal(String triggerId) {
        try {
            ViewsOpenRequest request = ViewsOpenRequest.builder()
                    .token(SLACK_BOT_TOKEN)
                    .triggerId(triggerId)
                    .view(View.builder()
                            .type("modal")
                            .title(ViewTitle.builder().type("plain_text").text("User Form").build())
                            .close(ViewClose.builder().type("plain_text").text("Close").build())
                            .submit(ViewSubmit.builder().type("plain_text").text("Submit").build())
                            .blocks(Arrays.asList(
                                    Blocks.input(input -> input
                                            .blockId("user_input")
                                            .element(PlainTextInputElement.builder()
                                                    .actionId("user_data")
                                                    .placeholder(PlainTextObject.builder().text("Enter your data here").build())
                                                    .build())
                                            .label(PlainTextObject.builder().text("Please enter your data").build())
                                    )
                            ))
                            .build())
                    .build();

            ViewsOpenResponse response = slack.methods().viewsOpen(request);
            if (!response.isOk()) {
                System.err.println("Error opening modal: " + response.getError());
            } else {
                System.out.println("Modal opened successfully");
            }
        } catch (IOException | SlackApiException e) {
            System.err.println("Error while opening modal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Map<String, String> parseFormData(String body) throws UnsupportedEncodingException {
        Map<String, String> formData = new HashMap<>();
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            formData.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return formData;
    }

    private static String parseFormBody(String body, String key) {
        try {
            Map<String, String> formData = new HashMap<>();
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                formData.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
            return formData.getOrDefault(key, "");
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error parsing form data: " + e.getMessage());
            return "";
        }
    }

}

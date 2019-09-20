package notelambda;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class NoteLambda implements RequestHandler<SNSEvent, String> {

    final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();

    public DynamoDB dynamoDB;

    public String table_name = "csye-6225";

    @Override
    public String handleRequest(SNSEvent input, Context context) {

        context.getLogger().log("NoteLambda function called");
        String email = input.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log("initializing db and email recieved : " + email);

        this.dynamoDB = new DynamoDB(ddb);
        long epochTime = Instant.now().getEpochSecond();
        epochTime += 120;                                  // adding 20 mins ttl
        String token = UUID.randomUUID().toString();        // uuid token

        context.getLogger().log("epoch : " + epochTime + " token :" + token);

        Table table = dynamoDB.getTable(table_name);
        Item item = table.getItem("id", email);

        long itemttl = 0; if (item != null) { itemttl = item.getLong("tokenTTL"); }

        if (item == null || (itemttl < epochTime - 120 && itemttl != 0)) {
            context.getLogger().log(String.valueOf(itemttl));
            context.getLogger().log(String.valueOf(epochTime));
            context.getLogger().log("Resetting password url and creating new record");
            Item record = new Item().withPrimaryKey("id", email)
                                      .withString("token", token)
                                      .withNumber("tokenTTL", epochTime);
            table.putItem(record);
            context.getLogger().log("Record saved to database");
            String dn = System.getenv("domainName");

            try {
                String mailText = "<center><img src=\"https://www.sbac.edu//cms/lib/FL02219191/Centricity/Domain/69/reset.PNG\" style=\"width:100px;height:100px\"><h1>Password Change Request</h1></center>" +
                        "<p>Hi,</p><p> We received a request to reset the password for the Application account associated with this e-mail address. Please click the link below to reset your password using our secure server</p><p> https://" + dn + "/reset?token=" + token + "&email=" + email + "</p>";
                sendMail(email, "resetpassword@" + dn, mailText, "Password reset request");
                context.getLogger().log("Password reset link sent");

            } catch (Exception ex) {
                context.getLogger().log("Error : " + ex.toString());
            }

        } else {
            context.getLogger().log("Record already exists for requested account, no action taken");
        }
        context.getLogger().log("NoteLambda function completed");

        return null;
    }

    private void initDynamoDB(Context context) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder
                                .standard()
                                .withRegion(Regions.US_EAST_1)
                                .build();
        context.getLogger().log(client.toString());
        this.dynamoDB = new DynamoDB(client);
    }

    private void sendMail(String to, String from, String mailText, String sub) {
        Destination destination = new Destination().withToAddresses(to);
        Content bodyContent = new Content()
                .withCharset("UTF-8")
                .withData(mailText);

        Body body = new Body().withHtml(bodyContent);

        Content subContent = new Content()
                .withCharset("UTF-8")
                .withData(sub);


        Message message = new Message()
                .withBody(body)
                .withSubject(subContent);


        AmazonSimpleEmailService mailClient = AmazonSimpleEmailServiceAsyncClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .build();

        SendEmailRequest emailRequest = new SendEmailRequest()
                .withDestination(destination)
                .withMessage(message)
                .withSource(from);


        SendEmailResult result = mailClient.sendEmail(emailRequest);

    }
}
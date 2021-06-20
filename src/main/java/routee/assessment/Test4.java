package routee.assessment;

import java.util.Optional;

public class Test4 implements ICallback {

    private final static RestClient restClient = RestClient.getInstance();

    public static void main(String args[]) {
        restClient.authenticateRouteeApiUser();
        PushControl pushControl = new PushControl(new Test4());
    }

    /**
     * Callback function invoked by  {@link routee.assessment.PushControl}.
     * Retrieves weather data from Weather Api and if succeeded sends an SMS via Routee Api.
     */
    @Override
    public void call() {
        Optional<Float> temp = restClient.getWeatherData();
        if (temp.isPresent()) {
            Optional<Boolean> pushResult = restClient.pushNotification(temp.get());
            if (pushResult.isPresent() && Boolean.TRUE.equals(pushResult.get())) {
                System.out.println("successfully sent an SMS to " + restClient.getPhoneNumber());
            } else {
                System.out.println("failed sending SMS");
            }
        } else {
            System.out.println("unable to retrieve weather data");
        }
    }

}

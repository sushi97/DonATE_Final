package com.sdl.app.tempdonate.Activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatEditText;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.instamojo.android.Instamojo;
import com.instamojo.android.activities.PaymentDetailsActivity;
import com.instamojo.android.callbacks.OrderRequestCallBack;
import com.instamojo.android.helpers.Constants;
import com.instamojo.android.models.Errors;
import com.instamojo.android.models.Order;
import com.instamojo.android.network.Request;
import com.sdl.app.tempdonate.R;
import com.sdl.app.tempdonate.Retrofit.APIClient;
import com.sdl.app.tempdonate.Retrofit.APIService;
import com.sdl.app.tempdonate.Retrofit.NGOList;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MoneyDonate extends AppCompatActivity {
    private ProgressDialog dialog;
    private AppCompatEditText nameBox, emailBox, phoneBox, amountBox, descriptionBox;
    private String currentEnv = null;
    private String accessToken = null;
    private List<String> nongovlist;
    private String urlpay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_money_donate);

        Log.e("TAG", "onCreate of money: ");

        Button button = (Button) findViewById(R.id.pay);
        nameBox = (AppCompatEditText) findViewById(R.id.name);
        nameBox.setSelection(nameBox.getText().toString().trim().length());
        emailBox = (AppCompatEditText) findViewById(R.id.email);
        emailBox.setSelection(emailBox.getText().toString().trim().length());
        phoneBox = (AppCompatEditText) findViewById(R.id.phone);
        phoneBox.setSelection(phoneBox.getText().toString().trim().length());
        amountBox = (AppCompatEditText) findViewById(R.id.amount);
        amountBox.setSelection(amountBox.getText().toString().trim().length());
        descriptionBox = (AppCompatEditText) findViewById(R.id.description);
        descriptionBox.setSelection(descriptionBox.getText().toString().trim().length());

        //final ArrayList<String> nongovlist = new ArrayList<>();
        APIService apiInterface;
        apiInterface = APIClient.getClient().create(APIService.class);

        retrofit2.Call<List<NGOList>> call = apiInterface.getOrgList();

        call.enqueue(new retrofit2.Callback<List<NGOList>>() {
            @Override
            public void onResponse(retrofit2.Call<List<NGOList>> call, retrofit2.Response<List<NGOList>> response) {
                List<NGOList> receiverlist = response.body();
                Log.e("TAG", "onResponse in money: ");
                nongovlist = new ArrayList<>();
                for (int i = 0; i < receiverlist.size(); i++) {
                    nongovlist.add(receiverlist.get(i).getName());
                }
                setSpinner();
            }

            @Override
            public void onFailure(retrofit2.Call<List<NGOList>> call, Throwable t) {
                Log.e("TAG", "onFailure: ");
                showToast("Error during fetching data from database!!");
                t.printStackTrace();
            }
        });

        urlpay = "https://api.instamojo.com/";

        Instamojo.setBaseUrl(urlpay);

        //pay button code.
        dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage("Please wait...");
        dialog.setCancelable(false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchTokenAndTransactionID();
            }
        });

        //let's set the log level to debug
        Instamojo.setLogLevel(Log.DEBUG);
    }

    public void setSpinner() {
        // create spinner to select nongovlist.
        Spinner spinner = (Spinner) findViewById(R.id.ngo_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nongovlist);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                String moneydonatengo = nongovlist.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                String moneydonatengo = nongovlist.get(0);
            }
        });
    }

    private HttpUrl.Builder getHttpURLBuilder() {
        return new HttpUrl.Builder()
                .scheme("https")
                .host("sample-sdk-server.instamojo.com");
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }


    /**
     * Fetch Access token and unique transactionID from developers server
     */
    private void fetchTokenAndTransactionID() {
        if (!dialog.isShowing()) {
            dialog.show();
        }

        OkHttpClient client = new OkHttpClient();
        HttpUrl url = getHttpURLBuilder()
                .addPathSegment("create")
                .build();

        RequestBody body = new FormBody.Builder()
                .add("env", urlpay.toLowerCase())
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }

                        showToast("Failed to fetch the Order Tokens");
                    }
                });
            }

            @Override
            public void onResponse(Call call, @NonNull Response response) throws IOException {
                String responseString;
                String errorMessage = null;
                String transactionID = null;
                responseString = response.body().string();
                response.body().close();

                try {
                    JSONObject responseObject = new JSONObject(responseString);
                    if (responseObject.has("error")) {
                        errorMessage = responseObject.getString("error");
                    } else {
                        accessToken = responseObject.getString("access_token");
                        transactionID = responseObject.getString("transaction_id");
                    }
                } catch (JSONException e) {
                    errorMessage = "Failed to fetch order tokens";
                }

                final String finalErrorMessage = errorMessage;
                final String finalTransactionID = transactionID;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }

                        if (finalErrorMessage != null) {
                            showToast(finalErrorMessage);
                            return;
                        }

                        createOrder(accessToken, finalTransactionID);
                    }
                });
            }
        });
    }


    private void createOrder(String accessToken, String transactionID) {
        String name = nameBox.getText().toString();
        final String email = emailBox.getText().toString();
        String phone = phoneBox.getText().toString();
        String amount = amountBox.getText().toString();
        String description = descriptionBox.getText().toString();

        //Create the Order
        Order order = new Order(accessToken, transactionID, name, email, phone, amount, description);

        //Validate the Order
        if (!order.isValid()) {
            //oops order validation failed. Pinpoint the issue(s).
            if (!order.isValidName()) {
                nameBox.setError("Buyer name is invalid");
            }

            if (!order.isValidEmail()) {
                emailBox.setError("Buyer email is invalid");
            }

            if (!order.isValidPhone()) {
                phoneBox.setError("Buyer phone is invalid");
            }

            if (!order.isValidAmount()) {
                amountBox.setError("Amount is invalid or has more than two decimal places");
            }

            if (!order.isValidDescription()) {
                descriptionBox.setError("Description is invalid");
            }

            if (!order.isValidTransactionID()) {
                showToast("Transaction is Invalid");
            }

            if (!order.isValidRedirectURL()) {
                showToast("Redirection URL is invalid");
            }

            return;
        }

        //Validation is successful. Proceed
        dialog.show();
        Request request = new Request(order, new OrderRequestCallBack() {

            @Override
            public void onFinish(final Order order, final Exception error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (error != null) {
                            if (error instanceof Errors.ConnectionError) {
                                showToast("No internet connection");
                            } else if (error instanceof Errors.ServerError) {
                                showToast("Server error. Try again");
                            } else if (error instanceof Errors.AuthenticationError) {
                                showToast("Access token is invalid or expired. Please Update the token.");
                            } else {
                                if (error instanceof Errors.ValidationError) {
                                    // Cast object to validation to pinpoint the issue
                                    Errors.ValidationError validationError = (Errors.ValidationError) error;

                                    if (!validationError.isValidTransactionID()) {
                                        showToast("Transaction ID is not Unique");
                                        return;
                                    }

                                    if (!validationError.isValidRedirectURL()) {
                                        showToast("Redirect url is invalid");
                                        return;
                                    }

                                    if (!validationError.isValidWebhook()) {
                                        showToast("Webhook url is invalid");
                                        return;
                                    }

                                    if (!validationError.isValidPhone()) {
                                        phoneBox.setError("Buyer's Phone Number is invalid/empty");
                                        return;
                                    }

                                    if (!validationError.isValidEmail()) {
                                        emailBox.setError("Buyer's Email is invalid/empty");
                                        return;
                                    }

                                    if (!validationError.isValidAmount()) {
                                        amountBox.setError("Amount is either less than Rs.9 or has more than two decimal places");
                                        return;
                                    }

                                    if (!validationError.isValidName()) {
                                        nameBox.setError("Buyer's Name is required");
                                        return;
                                    }
                                } else {
                                    showToast(error.getMessage());
                                }
                            }
                            return;
                        }

                        startPreCreatedUI(order);
                    }
                });
            }
        });

        request.execute();

    }

    private void startPreCreatedUI(Order order) {
        //Using Pre created UI
        Intent intent = new Intent(getBaseContext(), PaymentDetailsActivity.class);
        intent.putExtra(Constants.ORDER, order);
        startActivityForResult(intent, Constants.REQUEST_CODE);
    }


    private void checkPaymentStatus(final String transactionID, final String orderID) {
        if (accessToken == null || (transactionID == null && orderID == null)) {
            return;
        }

        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }

        showToast("Checking transaction status");
        OkHttpClient client = new OkHttpClient();
        HttpUrl.Builder builder = getHttpURLBuilder();
        builder.addPathSegment("status");

        if (transactionID != null && currentEnv != null) {
            builder.addQueryParameter("transaction_id", transactionID);
            builder.addQueryParameter("env", currentEnv.toLowerCase());
        } else {
            builder.addQueryParameter("id", orderID);
        }

        HttpUrl url = builder.build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        showToast("Failed to fetch the transaction status");
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseString = response.body().string();
                response.body().close();
                String status = null;
                String paymentID = null;
                String amount = null;
                String errorMessage = null;

                try {
                    JSONObject responseObject = new JSONObject(responseString);
                    JSONObject payment = responseObject.getJSONArray("payments").getJSONObject(0);
                    status = payment.getString("status");
                    paymentID = payment.getString("id");
                    amount = responseObject.getString("amount");

                } catch (JSONException e) {
                    errorMessage = "Failed to fetch the transaction status";
                }

                final String finalStatus = status;
                final String finalErrorMessage = errorMessage;
                final String finalPaymentID = paymentID;
                final String finalAmount = amount;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        if (finalStatus == null) {
                            showToast(finalErrorMessage);
                            return;
                        }

                        if (!finalStatus.equalsIgnoreCase("successful")) {
                            showToast("Transaction still pending");
                            return;
                        }

                        showToast("Transaction successful for id - " + finalPaymentID);


                        refundTheAmount(transactionID, finalAmount);
                    }
                });
            }
        });
    }


    /**
     * Will initiate a refund for a given transaction with given amount
     *
     * @param transactionID Unique identifier for the transaction
     * @param amount        amount to be refunded
     */
    private void refundTheAmount(String transactionID, String amount) {
        if (accessToken == null || transactionID == null || amount == null) {
            return;
        }

        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }

        showToast("Initiating a refund for - " + amount);
        OkHttpClient client = new OkHttpClient();
        HttpUrl url = getHttpURLBuilder()
                .addPathSegment("refund")
                .addPathSegment("")
                .build();

        RequestBody body = new FormBody.Builder()
                .add("env", currentEnv.toLowerCase())
                .add("transaction_id", transactionID)
                .add("amount", amount)
                .add("type", "PTH")
                .add("body", "Refund the Amount")
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        showToast("Failed to Initiate a refund");
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull final Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        String message;

                        if (response.isSuccessful()) {
                            message = "Refund initiated successfully";
                        } else {
                            message = "Failed to initiate a refund";
                        }

                        showToast(message);
                    }
                });
            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_CODE && data != null) {
            String orderID = data.getStringExtra(Constants.ORDER_ID);
            String transactionID = data.getStringExtra(Constants.TRANSACTION_ID);
            String paymentID = data.getStringExtra(Constants.PAYMENT_ID);

            //Check transactionID, orderID, and orderID for null before using them to check the Payment status.
            if (transactionID != null || paymentID != null) {
                checkPaymentStatus(transactionID, orderID);
            } else {
                showToast("Oops!! Payment was cancelled");
            }
        }
    }
}

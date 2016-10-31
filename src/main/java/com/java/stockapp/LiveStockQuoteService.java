package com.java.stockapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.scene.chart.XYChart;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;

/**
 *
 * @author mrusert
 */
public class LiveStockQuoteService extends ScheduledService<Stock> {
    private Stock stock;
    private String symbol;
    private String url_string;
    private final String DAILY_QUOTE_URL = "http://chartapi.finance.yahoo.com/instrument/1.0/%s/chartdata;type=quote;range=1d/csv";
    private final Pattern p = Pattern.compile("\\d{2}:[0-5]0");
    
    // Service to that checks for live stock price every minute
    public LiveStockQuoteService(String symbol) {
        this.symbol = symbol;
        this.url_string = String.format(DAILY_QUOTE_URL, this.symbol); 
    }
    
    @Override
    protected Task<Stock> createTask() {
        return new Task<Stock>() {
            @Override
            protected Stock call() {
                try {
                    stock = YahooFinance.get(symbol);
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());;
                }
               return stock;
            }
        };
    }
    
    // Function that creates a series of the current days stock price values (given stock price collected every minute by Yahoo Finance API)
    protected XYChart.Series<String, Number> getDailyQuote() throws IOException{
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        BufferedReader buf = null;        
        try {
            URL url = new URL(url_string);
            URLConnection conn = url.openConnection();
            buf = new BufferedReader(new InputStreamReader(conn.getInputStream()));
   
            String inputLine;
            int counter = 0;
            while((inputLine = buf.readLine()) != null) {
                counter++;
                if (counter > 17) {
                    try {
                        String[] strArr = inputLine.split(",");
                        SimpleDateFormat dFormat = new SimpleDateFormat("hh:mm");
                        Date d = new Date(Long.valueOf(strArr[0]) * 1000L);
                        String point = dFormat.format(d);
                        if (p.matcher(point).find()){
                            BigDecimal price = BigDecimal.valueOf(Double.valueOf(strArr[1])).setScale(2, BigDecimal.ROUND_UP);
                            series.getData().add(new XYChart.Data<String, Number>(point, price));
                        }
                    } catch(NumberFormatException ex) {
                        System.out.println("Error: " + ex.getMessage());
                    }
                }
            }
            
        } catch (MalformedURLException ex) {
            System.out.println(ex.getMessage());
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        } finally {
            if(buf != null) {
                buf.close();
            }
        }
        return series;
    }

}

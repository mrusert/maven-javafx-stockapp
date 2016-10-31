package com.java.stockapp;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.util.Duration;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;
import yahoofinance.quotes.stock.StockQuote;

public class FXMLController implements Initializable {
    
    // Variables for Scene Objects
    @FXML
    public Label name, price, bid, ask, error, lastTrade, dayHigh, dayLow;
    @FXML
    public TextField tf;
    @FXML
    public RadioButton liveQuote, dailyQuote, weeklyQuote, monthlyQuote;
    @FXML
    private ToggleGroup toggleGroup;
    @FXML
    private LineChart<String, Number> lineChart;
    @FXML
    private NumberAxis yAxis;
    
    // Controller Variables
    ObservableList<HistoricalQuote> historicalQuoteWeekly;
    ObservableList<HistoricalQuote> historicalQuoteMonthly;
    ObservableList<HistoricalQuote> historicalQuoteYearly;
    XYChart.Series<String, Number> historicalSeries;
    XYChart.Series<String, Number> liveSeries;
    Stock stock;
    Calendar calLastWeek;
    Calendar calLastMonth;
    Date now;
    LiveStockQuoteService liveQuoteService;
    OffsetDateTime timeNow;
    Calendar marketOpen;
    Calendar marketClose;
    
    // Search Stock Event
    @FXML
    private void handleButtonAction(ActionEvent event) throws IOException {
              
        if (!"".equals(tf.getText())) {
            
            // Clear variables for new stock search
            historicalQuoteWeekly = null;
            historicalQuoteMonthly = null;
            historicalQuoteYearly = null;
            now = null;
            stock = null;        
            error.setText("");
            if (historicalSeries != null) {
                lineChart.getData().remove(historicalSeries);
                historicalSeries = null;
            }
            if (liveSeries != null) {
                lineChart.getData().remove(liveSeries);
                liveSeries = null;
            }
            if (liveQuoteService != null){
                liveQuoteService.cancel();
            }
            
            
            // Create Calendar Objects which will be used to grab weekly, monthly, and yearly historical stock quote data
            calLastWeek = GregorianCalendar.getInstance();
            calLastWeek.add(Calendar.DATE,-7);
            calLastMonth = GregorianCalendar.getInstance();
            calLastMonth.add(Calendar.MONTH,-1);
            
            
            try {
                
                // Get Stock Quote
                stock = YahooFinance.get(tf.getText());
                StockQuote stockQuote = stock.getQuote();
                
                // Set Values for Stock Quote Labels
                name.setText(stock.getName());
                price.setText(stockQuote.getPrice().setScale(2, BigDecimal.ROUND_UP).toString());
                bid.setText(stockQuote.getBid().setScale(2, BigDecimal.ROUND_UP).toString());
                ask.setText(stockQuote.getAsk().setScale(2, BigDecimal.ROUND_UP).toString());
                dayHigh.setText(stockQuote.getDayHigh().setScale(2, BigDecimal.ROUND_UP).toString());
                dayLow.setText(stockQuote.getDayLow().setScale(2, BigDecimal.ROUND_UP).toString());
                
                // Grab Historical Stock Quotes using Calendar Objects
                historicalQuoteWeekly = FXCollections.observableArrayList(stock.getHistory(calLastWeek, Calendar.getInstance(), Interval.DAILY));
                historicalQuoteMonthly = FXCollections.observableArrayList(stock.getHistory(calLastMonth, Calendar.getInstance(), Interval.DAILY));
                historicalQuoteYearly = FXCollections.observableArrayList(stock.getHistory(Interval.MONTHLY));
                historicalSeries = new XYChart.Series<>();             
                historicalSeries = getHistoricalQuote(stock, historicalQuoteWeekly, historicalSeries, "weeklyQuote");
                
                // Instantiate Service to Grab Live Quotes
                liveQuoteService = new LiveStockQuoteService(tf.getText());
                liveSeries = new XYChart.Series<>();
                
                // Grab Series of current day stock data
                liveSeries = liveQuoteService.getDailyQuote();
                liveSeries.setName(stock.getQuote().getSymbol());
                
                // Set Chart Title to Company name
                lineChart.setTitle(stock.getName());
                
            } catch (IOException ex) {
                error.setText(ex.getMessage() + ". Please try again.");
                System.out.println("IOException: " + ex.getMessage());
            } catch (NullPointerException ex) {
                error.setText("Ticker symbol couldn't be found. Please try again.");
                System.out.println("NullPointerException: " + ex.getMessage());
            } 
            
            // Set Y Axis Range
            yAxis.setForceZeroInRange(false);
            yAxis.setAutoRanging(false);
            
            // Add Series with todays stock prices to LineChart
            lineChart.getData().add(liveSeries);
            toggleGroup.selectToggle(liveQuote);
            
            // Set variables to check if market is open
            timeNow = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime marketOpenDate = LocalDateTime.of(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH)+1, Calendar.getInstance().get(Calendar.DAY_OF_MONTH), 11, 0).atOffset(ZoneOffset.UTC);
            OffsetDateTime marketCloseDate = LocalDateTime.of(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH)+1, Calendar.getInstance().get(Calendar.DAY_OF_MONTH), 20, 0).atOffset(ZoneOffset.UTC);
         
            // If market is open, will instantiate service to check for price updates every minute
            if ( timeNow.isAfter(marketOpenDate) && timeNow.isBefore(marketCloseDate) ) {
                liveQuoteService.start();
                liveQuoteService.setPeriod(Duration.seconds(60));
                liveQuoteService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                    @Override
                    public void handle(WorkerStateEvent event) {
                        Stock stock = (Stock) event.getSource().getValue();
                        StockQuote stockQuote = stock.getQuote();
                        name.setText(stock.getName());
                        price.setText(stockQuote.getPrice().setScale(2, BigDecimal.ROUND_UP).toString());
                        bid.setText(stockQuote.getBid().setScale(2, BigDecimal.ROUND_UP).toString());
                        ask.setText(stockQuote.getAsk().setScale(2, BigDecimal.ROUND_UP).toString());
                        lastTrade.setText(stockQuote.getLastTradeTimeStr());
                        now = new Date();
                        liveSeries = getLiveQuote(stockQuote, now, liveSeries);
                        if (lineChart.getData().get(0) == liveSeries) {
                            yAxis.setLowerBound(stock.getQuote().getDayLow().doubleValue()- .50);
                            yAxis.setUpperBound(stock.getQuote().getDayHigh().doubleValue()+ .50);
                            yAxis.setTickUnit(.25);
                        }
                    }

                });
                liveQuoteService.setOnFailed(new EventHandler<WorkerStateEvent>() {
                    @Override
                    public void handle(WorkerStateEvent event) {
                        liveQuoteService.reset();
                    }
                });  
            } else {
                error.setText("The market is currently closed.");
            }
        } else {
            error.setText("No stock selected yet.");
        }
    }
    
    // Radio Button Event - Switch from Live Series Data to Weekly, Monthly or Yearly Series
    @FXML
    private void handleRadioAction(ActionEvent event) {

        if (!"".equals(tf.getText())) {
            if (historicalSeries != null) {
                lineChart.getData().remove(historicalSeries);
                lineChart.getData().remove(liveSeries);
                historicalSeries = new XYChart.Series<>();
            }
            String selectedId = ((RadioButton) toggleGroup.getSelectedToggle()).getId();
            switch (selectedId) {
                case "liveQuote":
                    lineChart.getData().remove(historicalSeries);
                    lineChart.getData().add(liveSeries);
                    yAxis.setAutoRanging(false);
                    yAxis.setLowerBound(stock.getQuote().getDayLow().doubleValue()- .50);
                    yAxis.setUpperBound(stock.getQuote().getDayHigh().doubleValue()+ .50);
                    yAxis.setTickUnit(.25);
                    break;
                case "weeklyQuote":
                    historicalSeries = getHistoricalQuote(stock, historicalQuoteWeekly, historicalSeries, selectedId);
                    lineChart.getData().remove(liveSeries);
                    lineChart.getData().add(historicalSeries);
                    yAxis.setAutoRanging(true);
                    break;
                case "monthlyQuote":
                    historicalSeries = getHistoricalQuote(stock, historicalQuoteMonthly, historicalSeries, selectedId);
                    lineChart.getData().remove(liveSeries);
                    lineChart.getData().add(historicalSeries);
                    yAxis.setAutoRanging(true);
                    break;
                case "yearlyQuote":
                    historicalSeries = getHistoricalQuote(stock, historicalQuoteYearly, historicalSeries, selectedId);
                    lineChart.getData().remove(liveSeries);
                    lineChart.getData().add(historicalSeries);
                    yAxis.setAutoRanging(true);
                    break;
                default:
                    historicalSeries = getHistoricalQuote(stock, historicalQuoteWeekly, historicalSeries, "weeklyQuote");
                    lineChart.getData().add(historicalSeries);
                    yAxis.setAutoRanging(true);
            }
        } else {
            error.setText("No stock selected yet.");
        }
    }
    
    // Function to update the Live Series with new Stock Data provided by LiveQuoteService
    protected XYChart.Series<String, Number> getLiveQuote(StockQuote stockQuote, Date time, XYChart.Series<String, Number> series) {
        
        SimpleDateFormat dFormat = new SimpleDateFormat("hh:mm");
        String dataPoint = dFormat.format(now);
        BigDecimal close = stockQuote.getPrice().setScale(2, BigDecimal.ROUND_UP);
        series.getData().add(new XYChart.Data<String, Number>(dataPoint, close));
      
        return series;
        
    }
    
    // Function to add historical quote data (weekly, monthly, yearly) to series
    private XYChart.Series<String, Number> getHistoricalQuote(Stock stock, ObservableList<HistoricalQuote> histQuote, XYChart.Series<String, Number> series, String quote) {
        
        String format;
    
        switch (quote) {
            case "weeklyQuote":
                format = "EEE-MMM-dd";
                break;
            case "monthlyQuote":
                format = "MMM-dd";
                break;
            case "yearlyQuote":
                format = "MMM-yy";
                break;
            default:
                format = "EEE-MMM-dd";
        }
        
        for (int i = histQuote.size() - 1; i >= 0; i--) {
            SimpleDateFormat dFormat = new SimpleDateFormat(format);
            Date date = histQuote.get(i).getDate().getTime();
            String dataPoint = dFormat.format(date);
            BigDecimal close = histQuote.get(i).getClose().setScale(2, BigDecimal.ROUND_UP);
            series.getData().add(new XYChart.Data<String, Number>(dataPoint, close));
            series.setName(stock.getSymbol());
        }

        return series;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
      
    }
    
}

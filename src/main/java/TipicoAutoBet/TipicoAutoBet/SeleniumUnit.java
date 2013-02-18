package TipicoAutoBet.TipicoAutoBet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Tipico AutoBet
 * Author: Axel Schroth
 * Date: 23.09.2012
 * Version: 1.0
 * 
 * About:
 * This tool is intended to bet automatically on a specific type of bets on tipico.com
 * with a predefined quote without any user interaction.
 * Run Tipico Auto Bet with a scheduler to get a fully automated solution.
 * 
 * Usage:
 * - Modify all parameters as required and set RELEASE = true
 * - Build runnable Jar file
 * - Run jar file on target system (JRE, Selenium & Firefox/Chrome required)
 * 
 * Server cronjob setup:
 *	crontab example (monthly): 0 0 1 * * java -jar /path/to/tipicoAutoBet/tipicoAutoBet.jar >> /path/to/tipicoAutoBet/log.txt
 */
public class SeleniumUnit 
{
	// Before release set this true
	public static final boolean RELEASE = true;
			
	// Starting with virtual account balance. This variable determines the amount of money which is
	// used to gamble. Using this virtual account enables me to put more money on the Tipico account in advance.
	// The amount of money I want to play with is set by this variable. [in Euro]
	int virtualAccountBalance;
	
	// this value defines odds before comma (e.g. 30 bets on odds 30, 30.3, 30.9 etc)
	int targetOdds;
	
	// try to find target odds multiple times..
	int maxTriesToFindBet = 100;
	// .. and wait in between a few milliseconds
	int intervalBetweenRefreshes = 2000;
	
	// Tipico login data
	String tipicoUsername;
	String tipicoPassword;

	// use which browser?
	boolean useChromeInsteadOfFirefox;
	
	// Program waits for user input before closing browser?
	boolean waitForInputBeforeClosingBrowser;
	
	// send report as mail?
	boolean sendReportAsMail;
	String recipientEmail;
	
	WebDriver driver;
	// location of the executable of the chromedriver (only required if using chrome instead of firefox!)
	String chromedriverPath;
	
	// report will be sent at the end
	String report = "Report of Tipico Auto Bet:\n\n";
	
	int expandedBets = 0;

	/*
	 * Constructor initializes all variables.
	 * TODO: CHANGE ACCOUNT DATA AND VARIOUS SETTINGS HERE!
	 */
	public SeleniumUnit()
	{
		if(RELEASE)
		{
			// Config for release version
			tipicoUsername = "freddyTheCat";
			tipicoPassword = "MieziKatze";
			useChromeInsteadOfFirefox = false;
			chromedriverPath = "";
			sendReportAsMail = true;
			waitForInputBeforeClosingBrowser = false;
			
			// Sample bet plan: Quotes - [15, 15, 30]: 5 Eur * 15 * 15 = ~1000 (max bet for one football bet) -> 1000 * 30 = 30000 Eur
			targetOdds = 15;
			virtualAccountBalance = 5;
		} else {
			// Config for debug version (this account is not charged so I can't bet by accident during development)
			tipicoUsername = "lorenaanevs";
			tipicoPassword = "<evol3";
			useChromeInsteadOfFirefox = false;
			chromedriverPath = "C:\\path\\to\\selenium\\chromedriver_win_23.0.1240.0\\chromedriver.exe";
			sendReportAsMail = false;
			waitForInputBeforeClosingBrowser = true;
			targetOdds = 2;
			virtualAccountBalance = 2;
		}
		
		recipientEmail = "your.email@gmail.com";
	}
	
	public void winSomeMoney()
	{
		// Create a new instance of the Firefox/Chrome driver
		
		if(useChromeInsteadOfFirefox)
		{
			System.setProperty("webdriver.chrome.driver", chromedriverPath);
			driver = new ChromeDriver();
		} else {
			driver = new FirefoxDriver();
		}
		
	
	    try {
	    	
			// Visit Website
			driver.get("https://www.tipico.com/de/online-sport-wetten/fussball/deutschland/1-bundesliga/g42301/");
			
			// Check if a welcome popup is blocking website and click button if needed
			closePopup();
			
			/* COMMENTED since direct link can be used
			// Find the navigation element football by its id and click it
			report("Navigating to football bets of 1. Bundesliga.");
			WebElement footballLink = driver.findElement(By.cssSelector("ul.level_1 li a span:contains('Fußball')"));
			footballLink.click();
			WebElement footballGermanyLink = driver.findElement(By.id("t30201"));
			footballGermanyLink.click();
			WebElement bundesligaLink = driver.findElement(By.id("t42301"));
			bundesligaLink.click();
			
			// Bets are rendered dynamically with JavaScript. Wait for content to load, timeout after 10 seconds
	        (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
	            public Boolean apply(WebDriver d) {
	                return !d.findElements(By.xpath("//div[contains(@class, 'sheet_head')]/descendant::div[starts-with(text(), '1. Bundesliga')]")).isEmpty();
	            }
	        });
		 	*/

	        
			// Expand all special bets in order to have more possibilities to find an appropriate bet
			expandAllBets();
			
			// Now try to find a bet with required odds several times!
			int tries = 0;
			List<WebElement> targetBetButtonList = new ArrayList<WebElement>();
			while(targetBetButtonList.isEmpty() && tries < maxTriesToFindBet)
			{
				tries++;
				
				report("Trying to find an appropriate bet (attempt nr " + tries + ")!");
				
				// look for an appropriate bet with given odds...
			    String xpathExpression = "//button[text() = '" + targetOdds + "']|//button[starts-with(text(),'" + targetOdds + ",')]";
				targetBetButtonList = driver.findElements(By.xpath(xpathExpression));
				
				if(targetBetButtonList.isEmpty())
				{
					// No bet found.. wait a moment until continuing with next try
					try {
						Thread.sleep(intervalBetweenRefreshes);
					} catch (InterruptedException e) {
						reportErr("Could not wait for next try. Interval to wait skipped!");
					}
					
					// refresh page
					driver.navigate().refresh();
					expandAllBets();
				
				} else {
					// Bet found!
					
					// Select random bet of all bets with appropriate quotes
					int randomBet = (new Random()).nextInt(targetBetButtonList.size());
					WebElement selectedTargetBetButton = targetBetButtonList.get(randomBet);
					report("Appropriate bet found (hits: " + targetBetButtonList.size() + ", tried " + tries + " times)! Odds: " + selectedTargetBetButton.getText());
					
					// Place bet
					report("Clicking bet.");
					selectedTargetBetButton.click();
					
					// Log in
					report("Logging in.");
					WebElement usernameTextinput = driver.findElement(By.id("login"));
					WebElement passwordTextinput = driver.findElement(By.id("password"));
					
					usernameTextinput.sendKeys(tipicoUsername);
					passwordTextinput.sendKeys(tipicoPassword);
					passwordTextinput.submit();
					
					// Check if another popup occured (question for being in Germany?)
					closePopup();
					
					// Read bet infos for report
					WebElement matchInfoDiv = driver.findElement(By.cssSelector("table.event_head tbody tr td:nth-child(2)"));
					WebElement betTypeInfoDiv = driver.findElement(By.cssSelector("div.tipp"));
					report("Bet on " + matchInfoDiv.getText() + " with " + betTypeInfoDiv.getText());
					
					// Type in bet amount (use virtual money account)
					report("Type in bet amount (" + virtualAccountBalance + " Eur).");
					WebElement betAmountTextinput = driver.findElement(By.id("editorForm:amountDisplay"));
					betAmountTextinput.clear();
					betAmountTextinput.sendKeys(Integer.toString(virtualAccountBalance).replace('.', ','));
					
					// Give website some time to update fields etc.
					Thread.sleep(2000);
					
					// Click bet button
					report("Click bet button.");
					WebElement betButton = driver.findElement(By.id("editorForm:reactionRepeat:0:cmdReaction"));
					betButton.click();
					
					// Confirm alert box
					report("Confirm alert box.");
					driver.switchTo().alert().accept();
					
					// TODO: Verify if bet was set correctly! (https://www.tipico.de/personal/ticket/ticketList.faces find bet and check status)
			        WebDriverWait wait = new WebDriverWait(driver, 10);
					try {
						@SuppressWarnings("unused")
						WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[contains(@class, 'left') and starts-with(., 'Der Wettschein mit dem Einsatz')]") ));
						report("Confirmation notification found. Bet set correctly.");
					} catch (TimeoutException e1) {
						reportErr("No confirmation notification found! Bet was not set correctly! Please check your bet overview manually.");
					}

				}
			}
			
			// TODO: read event time from https://www.tipico.de/personal/ticket/ticketList.faces
			//			then wait until there and check every 5 min if bet results are there -> restart if money left
			
			if(tries >= maxTriesToFindBet)
				reportErr("After several attempts, no appropriate bet was found! No bet was set.");
			
		} catch (Exception e1) {
			// Convert Stacktrace to String
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e1.printStackTrace(pw);

			reportErr("ERROR occured on execution of program! StackTrace: \n" +
					"\n" + sw.toString() );
			e1.printStackTrace();
		}
		
	    
	    if(waitForInputBeforeClosingBrowser)
	    {
	    	// Wait for user pressing any key before closing browser
	    	try {
				System.out.println("Done. Press any key to continue.");
				System.in.read();
			} catch (IOException e) {
				e.printStackTrace();
			}
	    }
		
	    // Close the browser
	    driver.quit();
	}

	/*
	 * Expand all visible bets to show and consider all available bets.
	 * Selecting by css, adjustments may be necessary if layout of website changes.
	 */
	private void expandAllBets() {
		String cssSelectorExpandableBets = "div.col * div.col_9:not(.m_on)";
		int nrExpandableBets = driver.findElements(By.cssSelector(cssSelectorExpandableBets)).size();
		
		report("Expanding bets (size: " + nrExpandableBets + ")");
		for(expandedBets = 0; expandedBets < nrExpandableBets; expandedBets++)
		{
			// get list of unexpended bets -> click first button
			List<WebElement> expandBetButtonList = driver.findElements(By.cssSelector(cssSelectorExpandableBets));
			expandBetButtonList.get(0).click();
			
			// Bets are rendered dynamically with JavaScript. Wait for content to load, timeout after 10 seconds
		    try {
				(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
				    public Boolean apply(WebDriver d) {
				        return (d.findElements(By.cssSelector("div#specialBets")).size() > expandedBets);
				    }
				});
			} catch (TimeoutException e) {
				reportErr("Timeout on waiting for expanded bet (expandedBets: " + expandedBets + ")");
			}
		}
	}

	
	/*
	 * Searches for popup and closes it if one was found.
	 * 	(Actually looks for button of popup instead of popup itself. Adjustments may be necessary if layout of website changes.)
	 */
	private void closePopup() {
		List<WebElement> popupButtonList = driver.findElements(By.className("flex_button_new_green"));
		if(!popupButtonList.isEmpty())
		{
			report("Popup Button found. Clicking button..");
			popupButtonList.get(0).click();
		}
		
		// Wait until popup is not visible any more
		try {
			(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			    public Boolean apply(WebDriver d) {
			        return d.findElements(By.xpath("//div[contains(@id, 'dim_layer') and contains(@style,'display: block')]")).isEmpty();
			    }
			});
		} catch (TimeoutException e) {
			reportErr("Timeout on waiting for popup to close.");
		}
	}
	
	public void report(String message)
	{
		report += message + "\n";
		System.out.println(message);
	}
	
	public void reportErr(String message)
	{
		report += "ERROR: " + message + "\n";
		System.err.println(message);
	}
	
	public void sendReportAsMail()
	{
		if(sendReportAsMail)
		{
			Email mailer = new Email();
			try {
				// send mail without attachements
				mailer.send(recipientEmail, "[TipicoAutoBet] Report of Tipico Auto Bet", report, new String[0]);
			} catch (Exception e) {
				System.err.println("Failed sending report mail!");
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		
		SeleniumUnit seleniumUnit = new SeleniumUnit();
		seleniumUnit.winSomeMoney();
		seleniumUnit.sendReportAsMail();

		
		System.exit(0);
	}
}

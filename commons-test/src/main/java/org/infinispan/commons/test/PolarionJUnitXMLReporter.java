package org.infinispan.commons.test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import org.testng.collections.Maps;
import org.testng.internal.IResultListener2;
import org.testng.internal.Utils;
import org.testng.reporters.XMLConstants;
import org.testng.reporters.XMLStringBuffer;

/**
 * A JUnit XML report generator for Polarion based on the JUnitXMLReporter
 *
 * @author <a href='mailto:afield[at]redhat[dot]com'>Alan Field</a>
 */
public class PolarionJUnitXMLReporter implements IResultListener2, ISuiteListener {
   private static final Pattern ENTITY = Pattern.compile("&[a-zA-Z]+;.*");
   private static final Pattern LESS = Pattern.compile("<");
   private static final Pattern GREATER = Pattern.compile(">");
   private static final Pattern SINGLE_QUOTE = Pattern.compile("'");
   private static final Pattern QUOTE = Pattern.compile("\"");
   private static final Map<String, Pattern> ATTR_ESCAPES = Maps.newHashMap();

   static {
      ATTR_ESCAPES.put("&lt;", LESS);
      ATTR_ESCAPES.put("&gt;", GREATER);
      ATTR_ESCAPES.put("&apos;", SINGLE_QUOTE);
      ATTR_ESCAPES.put("&quot;", QUOTE);
   }

   /**
    * keep lists of all the results
    */
   private AtomicInteger m_numFailed = new AtomicInteger(0);
   private AtomicInteger m_numSkipped = new AtomicInteger(0);
   private List<ITestResult> m_allTests = Collections.synchronizedList(Lists.<ITestResult>newArrayList());

   /**
    * @see org.testng.IConfigurationListener2#beforeConfiguration(ITestResult)
    */
   @Override
   public void beforeConfiguration(ITestResult tr) {
   }

   /**
    * @see org.testng.ITestListener#onTestStart(ITestResult)
    */
   @Override
   public void onTestStart(ITestResult result) {
   }

   /**
    * @see org.testng.ITestListener#onTestSuccess(ITestResult)
    */
   @Override
   public void onTestSuccess(ITestResult tr) {
      m_allTests.add(tr);
   }

   /**
    * @see org.testng.ITestListener#onTestFailure(ITestResult)
    */
   @Override
   public void onTestFailure(ITestResult tr) {
      m_allTests.add(tr);
      m_numFailed.incrementAndGet();
   }

   /**
    * @see org.testng.ITestListener#onTestFailedButWithinSuccessPercentage(ITestResult)
    */
   @Override
   public void onTestFailedButWithinSuccessPercentage(ITestResult tr) {
      m_allTests.add(tr);
      m_numFailed.incrementAndGet();
   }

   /**
    * @see org.testng.ITestListener#onTestSkipped(ITestResult)
    */
   @Override
   public void onTestSkipped(ITestResult tr) {
      m_allTests.add(tr);
      m_numSkipped.incrementAndGet();
   }

   /**
    * @see org.testng.ITestListener#onStart(ITestContext)
    */
   @Override
   public void onStart(ITestContext context) {
   }

   /**
    * @see org.testng.ITestListener#onFinish(ITestContext)
    */
   @Override
   public void onFinish(ITestContext context) {
   }

   /**
    * @see org.testng.ISuiteListener#onStart(ISuite)
    */
   @Override
   public void onStart(ISuite suite) {
      resetAll();
   }

   /**
    * @see org.testng.ISuiteListener#onFinish(ISuite)
    */
   @Override
   public void onFinish(ISuite suite) {
      generateReport(suite);
   }

   /**
    * @see org.testng.IConfigurationListener#onConfigurationFailure(org.testng.ITestResult)
    */
   @Override
   public void onConfigurationFailure(ITestResult itr) {
   }

   /**
    * @see org.testng.IConfigurationListener#onConfigurationSkip(org.testng.ITestResult)
    */
   @Override
   public void onConfigurationSkip(ITestResult itr) {
   }

   /**
    * @see org.testng.IConfigurationListener#onConfigurationSuccess(org.testng.ITestResult)
    */
   @Override
   public void onConfigurationSuccess(ITestResult itr) {
   }

   /**
    * generate the XML report given what we know from all the test results
    */
   protected void generateReport(ISuite suite) {
      XMLStringBuffer document = new XMLStringBuffer();
      document.addComment("Generated by " + getClass().getName());

      // Get elapsed time for testsuite element
      long elapsedTime = 0;
      for (ITestResult tr : m_allTests) {
         elapsedTime += (tr.getEndMillis() - tr.getStartMillis());
      }
      Properties attrs = new Properties();
      attrs.setProperty(XMLConstants.ATTR_TESTS, "" + m_allTests.size());
      attrs.setProperty(XMLConstants.ATTR_TIME, "" + elapsedTime / 1000.0);
      attrs.setProperty(XMLConstants.ATTR_NAME, getModuleSuffix());
      attrs.setProperty("skipped", "" + m_numSkipped);
      attrs.setProperty(XMLConstants.ATTR_ERRORS, "0");
      attrs.setProperty(XMLConstants.ATTR_FAILURES, "" + (m_numFailed.get()));
      document.push(XMLConstants.TESTSUITE, attrs);
      showProperties(document);

      document.addComment("Tests results");
      createElementFromTestResults(document, m_allTests);

      document.pop();

      // Reset output directory
      Utils.writeUtf8File(suite.getOutputDirectory().replaceAll("/Surefire suite", ""),
            generateFileName(suite) + ".xml", document.toXML());
   }

   private void createElementFromTestResults(XMLStringBuffer document, List<ITestResult> results) {
      synchronized (results) {
         for (ITestResult tr : results) {
            createElement(document, tr);
         }
      }
   }

   private void createElement(XMLStringBuffer doc, ITestResult tr) {
      Properties attrs = new Properties();
      long elapsedTimeMillis = tr.getEndMillis() - tr.getStartMillis();
      attrs.setProperty(XMLConstants.ATTR_NAME, testName(tr));
      attrs.setProperty(XMLConstants.ATTR_CLASSNAME, tr.getTestClass().getRealClass().getName());
      attrs.setProperty(XMLConstants.ATTR_TIME, "" + (((double) elapsedTimeMillis) / 1000));

      if ((ITestResult.FAILURE == tr.getStatus()) || (ITestResult.SKIP == tr.getStatus())) {
         doc.push(XMLConstants.TESTCASE, attrs);

         if (ITestResult.FAILURE == tr.getStatus()) {
            createFailureElement(doc, tr);
         } else if (ITestResult.SKIP == tr.getStatus()) {
            createSkipElement(doc, tr);
         }

         doc.pop();
      } else {
         doc.addEmptyElement(XMLConstants.TESTCASE, attrs);
      }
   }

   private void createFailureElement(XMLStringBuffer doc, ITestResult tr) {
      Properties attrs = new Properties();
      Throwable t = tr.getThrowable();
      if (t != null) {
         attrs.setProperty(XMLConstants.ATTR_TYPE, t.getClass().getName());
         String message = t.getMessage();
         if ((message != null) && (message.length() > 0)) {
            attrs.setProperty(XMLConstants.ATTR_MESSAGE, encodeAttr(message)); // ENCODE
         }
         doc.push(XMLConstants.FAILURE, attrs);
         doc.addCDATA(Utils.stackTrace(t, false)[0]);
         doc.pop();
      } else {
         doc.addEmptyElement(XMLConstants.FAILURE); // THIS IS AN ERROR
      }
   }

   private void createSkipElement(XMLStringBuffer doc, ITestResult tr) {
      doc.addEmptyElement("skipped");
   }

   private String encodeAttr(String attr) {
      String result = replaceAmpersand(attr, ENTITY);
      for (Map.Entry<String, Pattern> e : ATTR_ESCAPES.entrySet()) {
         result = e.getValue().matcher(result).replaceAll(e.getKey());
      }
      return result;
   }

   private String replaceAmpersand(String str, Pattern pattern) {
      int start = 0;
      int idx = str.indexOf('&', start);
      if (idx == -1) {
         return str;
      }
      StringBuilder result = new StringBuilder();
      while (idx != -1) {
         result.append(str.substring(start, idx));
         if (pattern.matcher(str.substring(idx)).matches()) {
            // do nothing it is an entity;
            result.append("&");
         } else {
            result.append("&amp;");
         }
         start = idx + 1;
         idx = str.indexOf('&', start);
      }
      result.append(str.substring(start));

      return result.toString();
   }

   /**
    * Reset all member variables for next test.
    */
   private void resetAll() {
      m_allTests = Collections.synchronizedList(Lists.<ITestResult>newArrayList());
      m_numFailed.set(0);
      m_numSkipped.set(0);
   }

   private String generateFileName(ISuite suite) {
      String name = getModuleSuffix();
      Collection<ISuiteResult> suiteResults = suite.getResults().values();
      if (suiteResults.size() == 1) {
         ITestNGMethod[] testMethods = suiteResults.iterator().next().getTestContext().getAllTestMethods();
         if (testMethods.length > 0) {
            Class<?> testClass = testMethods[0].getConstructorOrMethod().getDeclaringClass();
            // If only one test class executed, then use that as the filename
            String className = testClass.getName();
            // If only one test package executed, then use that as the filename
            String packageName = testClass.getPackage().getName();
            boolean oneTestClass = true;
            boolean oneTestPackage = true;
            for (ITestNGMethod method : testMethods) {
               if (!method.getConstructorOrMethod().getDeclaringClass().getName().equals(className)) {
                  oneTestClass = false;
               }
               if (!method.getConstructorOrMethod().getDeclaringClass().getPackage().getName().equals(packageName)) {
                  oneTestPackage = false;
               }
            }
            if (oneTestClass) {
               name = className;
            } else {
               if (oneTestPackage) {
                  name = packageName;
               }
            }
         } else {
            System.out.println("[" + this.getClass().getSimpleName() + "] " + "Test suite '" + name
                  + "' results have no test methods");
         }
      }
      return String.format("POLARION-%s", name);
   }

   private String testName(ITestResult res) {
      StringBuilder result = new StringBuilder();
      result.append(res.getTestClass().getRealClass().getName() + "." + res.getMethod().getMethodName());
      if (res.getMethod().getConstructorOrMethod().getMethod().isAnnotationPresent(Test.class)) {
         String dataProviderName = res.getMethod().getConstructorOrMethod().getMethod().getAnnotation(Test.class)
               .dataProvider();
         // Add parameters for methods that use a data provider only
         if (res.getParameters().length != 0 && (dataProviderName != null && !dataProviderName.isEmpty())) {
            result.append("(").append(Arrays.deepToString(res.getParameters())).append(")");
         }
      }
      return result.toString();
   }

   private void showProperties(XMLStringBuffer report) {
      report.push(XMLConstants.PROPERTIES);

      // Add all system properties
      report.addComment("Java System properties");
      for (Object key : System.getProperties().keySet()) {
         Properties property = new Properties();
         property.setProperty(XMLConstants.ATTR_NAME, key.toString());
         property.setProperty(XMLConstants.ATTR_VALUE, System.getProperty(key.toString()));
         report.addEmptyElement(XMLConstants.PROPERTY, property);
      }

      // Add all environment variables
      report.addComment("Environment variables");
      for (String key : System.getenv().keySet()) {
         Properties property = new Properties();
         property.setProperty(XMLConstants.ATTR_NAME, key.toString());
         property.setProperty(XMLConstants.ATTR_VALUE, System.getenv(key.toString()));
         report.addEmptyElement(XMLConstants.PROPERTY, property);
      }

      report.pop();
   }

   private String getModuleSuffix() {
      // Remove the "-" from the beginning of the string
      return System.getProperty("infinispan.module-suffix").substring(1);
   }
}

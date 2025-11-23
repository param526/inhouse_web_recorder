Feature: Flow Check

  Scenario: Check Flow
    Given Given I visit "Sign In" page
    And Given I visit "" page
    When I enter "Fatech.user" into the "User name or email"
    And I click on the "Password"
    And I enter "Intercol@2022b" into the "Password"
    And I click on the "Sign In"
    Given Given I visit "Welcome" page
    And Given I visit "Oracle Fusion Cloud Applications" page
    When I click on the "pt1:_UIScmil2u"
    Given Given I visit "Single Sign-Off consent" page
    And Given I visit "Sign In" page

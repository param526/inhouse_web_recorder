Feature: Cryoport Login Flow


  Scenario: Success 1
    Given I visit "Sign In" page
    Then I am on "" page
    When I enter "Fatech.user" into the "User Name"
    And I enter "Intercol@2022b" into the "Password"
    When I click on the "Sign In" button
    Then I am on "Welcome" page
    When I click on the "Home" link
    Then I am on "Oracle Fusion Cloud Applications" page
    When I click on the "Workspace" link
    And I click on the "Workspace" link
    Then I am on "Workspace - Oracle Fusion Cloud Applications" page
    When I click on the "PAYMENT INTERNATIONAL ENT.BSC" link
    And I click on the "Profile" link
    And I click on the "Industries" link
    And I click on the "Search: Primary Contact" link
    And I click on the "Search: Primary Contact" link
    When I enter "sdffsd" into the "URL"
    When I click on the "Actions" link
    And I click on the "Delete Account"
    And I click on the "Cancel" button
    And I click on the "Cancel" button
    And I click on the "Home" link
    Then I am on "Oracle Fusion Cloud Applications" page
    When I click on the "Settings and Actions" link
    And I click on the "Company Single Sign-On" button
    Then I am on "Single Sign-Off consent" page
    When I click on the "Confirm" button
    Then I am on "Sign In" page

  Scenario: Success 2
    Given I visit "Sign In" page
    Then I am on "" page
    When I enter "Fatech.user" into the "User Name"
    And I enter "Intercol@2022b" into the "Password"
    When I click on the "Sign In" button
    Then I am on "Welcome" page
    When I click on the "Home" link
    Then I am on "Oracle Fusion Cloud Applications" page
    When I click on the "Settings and Actions" link
    And I click on the "Company Single Sign-On" button
    Then I am on "Single Sign-Off consent" page
    When I click on the "Confirm" button
    Then I am on "Sign In" page

Scenario: dfsdf
Given I visit "Sign In" page
Then I am on "Cloud Sign In" page

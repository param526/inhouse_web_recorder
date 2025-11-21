Feature: Oracle User Login

  Scenario: Failed
    Given I visit "Sign In" page
    Given I visit "" page
    When I enter "sasafasf" into the "User name or email"
    When I click on the "Password"
    When I enter "dfdsfsd" into the "Password"
    Then I click on the "Sign In"

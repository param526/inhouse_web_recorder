Feature: User Login Oracle

  Scenario: Failure
    Given I visit "Sign In" page
    And I visit "" page
    When I enter "sasafasf" into the "User name or email"
    And I click on the "Password"
    And I enter "dfdsfsd" into the "Password"
    Then I click on the "Sign In"

  Scenario: Success
    Given I visit "Sign In" page
    And I visit "" page
    When I enter "sasafasf" into the "User name or email"
    And I click on the "Password"
    And I enter "dfdsfsd" into the "Password"
    Then I click on the "Sign In"

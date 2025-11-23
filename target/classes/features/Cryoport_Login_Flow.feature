Feature: Cryoport Login Flow

  Scenario: Success
    Given I visit "Cryoport - Cryoportal® 2" page
    When I enter "cryoadmin@osidigital.com" into the "email"
    And I enter "EnTropYoSi" into the "password"
    When I check the "remember_me" option
    And I click on the "Sign in" button
    Then I am on "Cryoport - Science, Logistics, Certainty" page
    When I click on the "Sign Out" link
    Then I am on "Cryoport - Cryoportal® 2" page

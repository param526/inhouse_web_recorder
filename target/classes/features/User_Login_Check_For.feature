Feature: User Login Check For

  Scenario: Success
    Given I navigate to "Cryoport - Cryoportal® 2" page
    When I enter "cryoadmin@osidigital.com" into the "email"
    And I enter "EnTropYoSi" into the "password"
    And I click on the "Remember me on this device"
    And I click on the "remember_me"
    And I enter "yes" into the "remember_me"
    And I click on the "Sign in" button
    Given I navigate to "Cryoport - Science, Logistics, Certainty" page
    When I click on the "Sign Out" link
    Given I navigate to "Cryoport - Cryoportal® 2" page

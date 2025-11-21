Feature: User Login

  Scenario: Login Success
    Given I visit "Cryoport - Cryoportal� 2" page
    When I click on the "email"
    When I enter "cryoadmin@osidigital.com" into the "email"
    When I click on the "password"
    When I enter "EnTropYoSi" into the "password"
    When I click on the "remember_me"
    When I enter "yes" into the "remember_me"
    Given I visit "Cryoport - Science, Logistics, Certainty" page
    Given I visit "Cryoport - Cryoportal� 2" page

Feature: User Login

  Scenario: Success
    Given I visit "Cryoport - Cryoportal® 2" page
    When I click on the "email"
    When I enter "cryoadmin@osidigital.com" into the "email"
    When I enter "EnTropYoSi" into the "password"
    When I click on the "remember_me"
    When I enter "yes" into the "remember_me"
    Given I visit "Cryoport - Science, Logistics, Certainty" page
    Given I visit "Cryoport - Cryoportal® 2" page

  Scenario: Failed
    Given I visit "Cryoport - Cryoportal® 2" page
    When I click on the "email"
    When I enter "cryoadmin@osidigital.com" into the "email"
    When I enter "EnTropYoSi" into the "password"
    When I click on the "remember_me"
    When I enter "yes" into the "remember_me"
    Given I visit "Cryoport - Science, Logistics, Certainty" page
    Given I visit "Cryoport - Cryoportal® 2" page

  Scenario: Success
    Given I visit "Cryoport - Cryoportal® 2" page
    When I enter "ppentaparthy@osidigital.com" into the "email"
    When I click on the "email"
    When I click on the "email"
    When I enter "cryoadmin@osidigital.com" into the "email"
    When I enter "EnTropYoSi" into the "password"
    When I click on the "remember_me"
    When I enter "yes" into the "remember_me"
    Given I visit "Cryoport - Science, Logistics, Certainty" page
    Given I visit "Cryoport - Cryoportal® 2" page

  Scenario: Failed
    Given I visit "Cryoport - Cryoportal® 2" page
    When I enter "ppentaparthy@osidigital.com" into the "email"
    When I click on the "email"
    When I click on the "email"
    When I enter "cryoadmin@osidigital.com" into the "email"
    When I enter "EnTropYoSi" into the "password"
    When I click on the "remember_me"
    When I enter "yes" into the "remember_me"
    Given I visit "Cryoport - Science, Logistics, Certainty" page
    Given I visit "Cryoport - Cryoportal® 2" page

  Scenario: Success
    Given I visit "Cryoport - Cryoportal® 2" page
    When I enter "ppentaparthy@osidigital.com" into the "email"
    When I click on the "email"
    When I click on the "email"
    When I enter "cryoadmin@osidigital.com" into the "email"
    When I enter "EnTropYoSi" into the "password"
    When I click on the "remember_me"
    When I enter "yes" into the "remember_me"
    Given I visit "Cryoport - Science, Logistics, Certainty" page
    Given I visit "Cryoport - Cryoportal® 2" page

  Scenario: Failed
    Given I visit "Cryoport - Cryoportal® 2" page
    When I enter "ppentaparthy@osidigital.com" into the "email"
    When I click on the "email"
    When I click on the "email"
    When I enter "cryoadmin@osidigital.com" into the "email"
    When I enter "EnTropYoSi" into the "password"
    When I click on the "remember_me"
    When I enter "yes" into the "remember_me"
    Given I visit "Cryoport - Science, Logistics, Certainty" page
    Given I visit "Cryoport - Cryoportal® 2" page

Scenario: Failure
Given I visit "Sign In" page
And I visit "" page
When I enter "sasafasf" into the "User name or email"
And I click on the "Password"
And I enter "dfdsfsd" into the "Password"
Then I click on the "Sign In"

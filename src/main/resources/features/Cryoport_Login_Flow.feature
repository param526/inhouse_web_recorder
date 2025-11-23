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

  Scenario: Success 1
    Given I visit "Cryoport - Cryoportal® 2" page
    When I enter "cryoadmin@osidigital.com" into the "email"
    And I enter "EnTropYoSi" into the "password"
    When I click on the "Sign in" button
    Then I am on "Cryoport - Science, Logistics, Certainty" page
    When I interact with the unlabeled i element
    Then I am on "Cryoport - Science, Logistics, Certainty" page
    When I interact with the unlabeled i element
    Then I am on "Cryoport - Science, Logistics, Certainty" page
    When I interact with the unlabeled link element
    And I click on the "New Order"
    And I select the "Adina Kern-Goldberger (ANDR153)" option
    And I click on the "Save & Continue" button
    Then I am on "Cryoport - Science, Logistics, Certainty" page
    When I select the "CLEVELAND CLINIC FERTILITY CENTER (CLEVELAND CLINIC FERTILITY CENTER) Arsela Gishto (gishtoa@ccf.org)  26900 CEDAR RD STE 220S, BEACHWOOD, OH, 44122-1157" option
    And I select the "FERTILITY LABORATORIES OF COLORADO (CCRM) (FERTILITY LABORATORIES OF COLORADO (CCRM)) Kristin Costine (kcostine@colocrm.com)  10290 RIDGEGATE CIR, LONE TREE, CO, 80124-5331" option
    And I click on the "Save & Continue" button
    Then I am on "Cryoport - Science, Logistics, Certainty" page
    When I select the "TEST SHIPPER - TEST SHIPPER" option
    And I click on the "Add Commodity" link
    And I select the "Human Embryo(s)" option
    And I interact with the unlabeled div element
    When I enter "sadads" into the "shippers[shippers_attributes][0][commodities_attributes][0][primary_container_type_description]"
    When I check the "shippers[shippers_attributes][0][commodities_attributes][0][leg_numbers][]" option
    And I click on the "Accessory" link
    And I interact with the unlabeled presentation element
    And I select the "ACC-10662 - CRYOTHAW KIT" option
    And I click on the "Save & Continue" button
    Then I am on "Cryoport - Science, Logistics, Certainty" page
    When I click on the "Save & Continue" button
    Then I am on "Cryoport - Science, Logistics, Certainty" page

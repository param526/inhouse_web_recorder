Feature: Oracle Login Flow

Scenario: Success
Given I visit "Sign In" page
And I visit "" page
When I enter "" into the "User name or email"
And I click on the "Password"
And I enter "" into the "Password"
And I click on the "Sign In"
Given I visit "Welcome" page
When I click on the "pt1:_UIScmil2u"
Given I visit "Single Sign-Off consent" page
And I visit "Sign In" page

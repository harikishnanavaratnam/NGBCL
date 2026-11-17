# Badminton Tournament Management System

## Software Requirements Specification (SRS)

**Document version:** 1.0\
**Date:** 17 November 2026\
**Project:** 1st Edition Badminton Tournament Management System\
**Status:** Draft for review

------------------------------------------------------------------------

## 1. Introduction

### 1.1 Purpose

This document defines the functional and non-functional requirements for
a web-based Badminton Tournament Management System. The system will
manage the tournament lifecycle, including public player and team
registration, approval workflows, player auctions, team formation,
fixtures, match scoring, standings, statistics, announcements, and
administration.

The system is intended to support the first edition of the tournament
while using a data model that can support future editions.

### 1.2 Scope

The system will provide:

-   A public tournament website.
-   Public player registration without requiring an account.
-   Public team registration without requiring an account.
-   Administrative review and approval of registrations.
-   Player and team profile management, including photographs and logos.
-   A live auction operated by an authorised auctioneer.
-   Team budget and squad-size enforcement.
-   Team-versus-team fixtures containing individual badminton matches.
-   Match scheduling and game-by-game scoring.
-   Live public updates for auctions and matches.
-   Tournament standings, player statistics, announcements, and reports.
-   File storage for photographs, logos, and supporting documents.

### 1.3 Goals

1.  Reduce manual work involved in registrations, auctions, fixtures,
    and scorekeeping.
2.  Provide a transparent and auditable auction process.
3.  Make approved tournament information available to the public.
4.  Ensure match results consistently update team standings and player
    statistics.
5.  Provide a reusable platform for future tournament editions.

### 1.4 Definitions

  -----------------------------------------------------------------------
  Term                                Meaning
  ----------------------------------- -----------------------------------
  Tournament                          A specific edition or instance of
                                      the competition.

  Player                              An individual who registers to
                                      participate.

  Team                                A group of players competing under
                                      one team identity.

  Auction                             The process through which eligible
                                      players are assigned to teams for a
                                      winning bid.

  Team fixture                        A competition between two teams.

  Individual match                    A singles or doubles badminton
                                      match within a team fixture.

  Game                                One game within an individual
                                      badminton match.

  Registration                        A submitted application awaiting or
                                      completing administrative review.

  Auctioneer                          An authorised user who controls the
                                      live auction.
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 2. Users and Roles

The system shall support role-based access control.

  -----------------------------------------------------------------------
  Role                                Main responsibilities
  ----------------------------------- -----------------------------------
  Super Administrator                 Full system access, user and role
                                      management, and system
                                      configuration.

  Tournament Administrator            Tournament setup, registrations,
                                      teams, auction settings, fixtures,
                                      results, and reports.

  Registration Manager                Review, approve, reject, and manage
                                      player and team applications.

  Auctioneer                          Operate the auction, open players
                                      for bidding, accept bids, and close
                                      auction lots.

  Team Manager                        View their team's information,
                                      budget, roster, fixtures, and
                                      results. This role is optional and
                                      may be enabled if required.

  Match Scorer / Official             Enter and update scores for
                                      assigned matches.

  Public Visitor                      View published tournament
                                      information, approved players and
                                      teams, auction outcomes, fixtures,
                                      results, and standings.
  -----------------------------------------------------------------------

### 2.1 Authentication

-   Public player and team registration shall not require an account.
-   Administrative functions shall require authenticated accounts.
-   Team Manager accounts, if enabled, shall be linked to a specific
    team and restricted to that team's data.
-   The system shall enforce permissions on the backend, not only
    through frontend visibility.

------------------------------------------------------------------------

## 3. System Overview

### 3.1 Main Components

1.  **Public Website**
    -   Tournament information
    -   Player registration
    -   Team registration
    -   Approved player and team directory
    -   Auction results
    -   Fixtures and schedules
    -   Live match scores
    -   Standings and statistics
    -   Announcements
2.  **Admin Portal**
    -   Tournament configuration
    -   Registration review
    -   Player and team management
    -   Skill category management
    -   Auction configuration
    -   Fixture and match management
    -   User and role management
    -   Reports and audit logs
3.  **Auctioneer Console**
    -   Auction player queue
    -   Current player details
    -   Current highest bid
    -   Bid controls
    -   Sold / unsold / withdrawn actions
    -   Team budget and squad information
4.  **Public Auction Display**
    -   Current player
    -   Player photograph and relevant details
    -   Current bid and bidding team
    -   Auction status
    -   Recent sold players and team summaries
5.  **Match Scorer Console**
    -   Assigned matches
    -   Match and player details
    -   Game-by-game score entry
    -   Match completion and submission
    -   Result confirmation workflow, where required

### 3.2 High-Level Workflow

1.  Administrator creates and configures a tournament.
2.  Administrator configures player skill categories and registration
    requirements.
3.  Players and teams submit public registration forms.
4.  Registration staff review applications.
5.  Approved players and teams become publicly visible.
6.  Approved players are made eligible for the auction.
7.  Administrator configures auction budget, squad limit, and bidding
    rules.
8.  Auctioneer conducts the live auction.
9.  Successful auction transactions allocate players to teams.
10. Administrator creates the tournament format, fixtures, and
    individual matches.
11. Scorers enter match scores.
12. Confirmed results update standings and statistics.
13. Public visitors view the latest published tournament information.

------------------------------------------------------------------------

## 4. Functional Requirements

Requirement IDs are included for traceability and future testing.

### 4.1 Tournament Management

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  TRN-001                 The system shall allow  Must
                          an authorised           
                          administrator to create 
                          a tournament.           

  TRN-002                 The system shall store  Must
                          the tournament name,    
                          edition, description,   
                          dates, venue, status,   
                          and branding.           

  TRN-003                 The system shall allow  Must
                          administrators to       
                          configure registration  
                          opening and closing     
                          dates.                  

  TRN-004                 The system shall        Must
                          support draft,          
                          upcoming, active,       
                          completed, and archived 
                          tournament statuses.    

  TRN-005                 The system shall allow  Must
                          administrators to       
                          configure               
                          tournament-specific     
                          settings.               

  TRN-006                 The system should       Should
                          support multiple        
                          tournament editions     
                          without mixing their    
                          players, teams, auction 
                          data, fixtures, or      
                          results.                
  -----------------------------------------------------------------------

### 4.2 Public Player Registration

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  PLY-001                 A visitor shall be able Must
                          to submit a player      
                          registration without    
                          creating an account.    

  PLY-002                 The registration form   Must
                          shall collect the       
                          player's configured     
                          personal and contact    
                          details.                

  PLY-003                 The form shall support  Must
                          uploading a player      
                          photograph.             

  PLY-004                 The player may select a Must
                          skill category from the 
                          active categories       
                          configured by the       
                          administrator.          

  PLY-005                 Skill category shall be Must
                          optional unless the     
                          administrator           
                          configures it as        
                          mandatory.              

  PLY-006                 The system shall        Must
                          validate required       
                          fields, formats, file   
                          types, and file sizes.  

  PLY-007                 The system shall create Must
                          a registration with a   
                          unique registration     
                          reference and PENDING   
                          status after successful 
                          submission.             

  PLY-008                 The system shall show a Must
                          submission confirmation 
                          and registration        
                          reference.              

  PLY-009                 The system should       Should
                          notify the applicant    
                          that the registration   
                          has been received.      

  PLY-010                 The system shall detect Must
                          possible duplicate      
                          registrations using     
                          configurable matching   
                          fields such as email    
                          and phone number.       

  PLY-011                 Duplicate detection     Must
                          shall not automatically 
                          merge records; an       
                          authorised              
                          administrator shall     
                          resolve possible        
                          duplicates.             

  PLY-012                 Only approved players   Must
                          shall appear in the     
                          public player directory 
                          and be eligible for     
                          auction.                

  PLY-013                 The administrator shall Must
                          be able to edit         
                          registration details    
                          while preserving an     
                          audit history.          
  -----------------------------------------------------------------------

**Suggested player registration fields**

-   Full name
-   Date of birth
-   Gender
-   Email address
-   Contact number
-   Address
-   Player photograph
-   Optional skill category
-   Playing experience / short description
-   Emergency contact name and number, if required
-   Applicant declaration and consent

The final set of personal data fields should be confirmed before
implementation. The system should collect only information needed to
administer the tournament.

### 4.3 Player Approval and Management

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  PLY-020                 Registration staff      Must
                          shall be able to view   
                          pending player          
                          applications.           

  PLY-021                 Staff shall be able to  Must
                          approve or reject an    
                          application.            

  PLY-022                 Staff shall be able to  Must
                          record an internal note 
                          and a rejection reason. 

  PLY-023                 The system shall record Must
                          who reviewed an         
                          application and when.   

  PLY-024                 An approved             Must
                          registration shall be   
                          linked to a player      
                          profile.                

  PLY-025                 A player photograph     Must
                          shall be uploaded and   
                          verified before         
                          approval.               

  PLY-026                 Approved player details Must
                          and photographs shall   
                          be publicly visible     
                          according to tournament 
                          publication settings.   

  PLY-027                 The administrator shall Must
                          be able to deactivate   
                          or withdraw a player    
                          without deleting        
                          historical auction or   
                          match records.          

  PLY-028                 The system shall        Must
                          prevent an ineligible,  
                          rejected, withdrawn, or 
                          unapproved player from  
                          entering the auction.   
  -----------------------------------------------------------------------

### 4.4 Skill Category Management

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  CAT-001                 Administrators shall be Must
                          able to create          
                          tournament-specific     
                          player skill            
                          categories.             

  CAT-002                 Administrators shall be Must
                          able to edit category   
                          names and descriptions. 

  CAT-003                 Administrators shall be Must
                          able to activate or     
                          deactivate categories.  

  CAT-004                 Administrators shall be Should
                          able to define display  
                          order.                  

  CAT-005                 Deactivated categories  Must
                          shall not be offered    
                          for new registrations   
                          but shall remain        
                          available for           
                          historical records.     

  CAT-006                 A player's skill        Must
                          category shall be       
                          optional unless         
                          configured otherwise.   
  -----------------------------------------------------------------------

Examples may include Beginner, Intermediate, and Advanced. These are
examples only; the administrator defines the actual categories.

### 4.5 Public Team Registration and Team Management

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  TEM-001                 A visitor shall be able Must
                          to submit a team        
                          application without     
                          creating an account.    

  TEM-002                 The team application    Must
                          shall collect the team  
                          name and designated     
                          contact person's        
                          details.                

  TEM-003                 The application shall   Must
                          support uploading a     
                          team logo.              

  TEM-004                 The system shall create Must
                          a PENDING team          
                          registration after      
                          successful submission.  

  TEM-005                 The system shall        Must
                          provide a registration  
                          reference and           
                          submission              
                          confirmation.           

  TEM-006                 An authorised           Must
                          administrator shall be  
                          able to approve or      
                          reject a team           
                          application.            

  TEM-007                 A team logo shall be    Must
                          uploaded and verified   
                          before approval.        

  TEM-008                 Only approved teams     Must
                          shall be publicly       
                          visible and eligible to 
                          participate in the      
                          tournament.             

  TEM-009                 Administrators shall be Must
                          able to create teams    
                          directly without a      
                          public application.     

  TEM-010                 The system shall record Must
                          whether a team was      
                          created through public  
                          registration or         
                          directly by an          
                          administrator.          

  TEM-011                 Team names shall be     Must
                          unique within a         
                          tournament.             

  TEM-012                 Administrators shall be Must
                          able to edit, activate, 
                          or deactivate teams     
                          while preserving        
                          historical records.     

  TEM-013                 The system shall        Must
                          display approved team   
                          logos and public team   
                          information.            
  -----------------------------------------------------------------------

**Suggested team registration fields**

-   Team name
-   Team logo
-   Contact person's name
-   Contact email
-   Contact phone number
-   Short team description, optional

### 4.6 Auction Configuration

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  AUC-001                 An administrator shall  Must
                          be able to create an    
                          auction for a           
                          tournament.             

  AUC-002                 The administrator shall Must
                          configure the same      
                          initial budget for all  
                          participating teams.    

  AUC-003                 The administrator shall Must
                          configure the maximum   
                          squad size for each     
                          team.                   

  AUC-004                 The administrator shall Must
                          configure the base      
                          price and minimum bid   
                          increment.              

  AUC-005                 The administrator shall Should
                          be able to configure    
                          whether re-auctioning   
                          unsold players is       
                          permitted.              

  AUC-006                 The system shall create Must
                          a team budget record    
                          for each eligible team. 

  AUC-007                 The system shall retain Must
                          the auction             
                          configuration used when 
                          the auction starts.     

  AUC-008                 Changes to auction      Must
                          settings after bidding  
                          begins shall be         
                          restricted to           
                          authorised              
                          administrators and      
                          recorded in the audit   
                          log.                    
  -----------------------------------------------------------------------

### 4.7 Live Auction

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  AUC-020                 The auctioneer shall be Must
                          able to start, pause,   
                          and resume an auction.  

  AUC-021                 The auctioneer shall be Must
                          able to select or       
                          advance to the next     
                          eligible player.        

  AUC-022                 The auction display     Must
                          shall show the current  
                          player's approved       
                          public profile and      
                          photograph.             

  AUC-023                 The system shall        Must
                          display the current     
                          highest bid and bidding 
                          team.                   

  AUC-024                 The system shall allow  Must
                          authorised users to     
                          place bids through the  
                          auction console.        

  AUC-025                 The backend shall       Must
                          validate every bid      
                          against the current     
                          bid, minimum increment, 
                          team budget, and squad  
                          limit.                  

  AUC-026                 The system shall reject Must
                          a bid that exceeds the  
                          team's available budget 
                          or violates the         
                          configured rules.       

  AUC-027                 The system shall        Must
                          prevent a team from     
                          purchasing more players 
                          than its maximum squad  
                          size.                   

  AUC-028                 The auctioneer shall be Must
                          able to close a player  
                          as SOLD, UNSOLD, or     
                          WITHDRAWN.              

  AUC-029                 When a player is sold,  Must
                          the system shall record 
                          the winning team,       
                          winning bid, final      
                          price, and transaction  
                          time.                   

  AUC-030                 A player shall not be   Must
                          sold to more than one   
                          team in the same        
                          tournament auction.     

  AUC-031                 The system shall update Must
                          the team's spent and    
                          remaining budget        
                          atomically when a sale  
                          is confirmed.           

  AUC-032                 The system shall        Must
                          provide a live public   
                          auction display.        

  AUC-033                 Auction state changes   Must
                          shall be delivered to   
                          connected clients in    
                          near real time.         

  AUC-034                 The system shall        Must
                          maintain an immutable   
                          or auditable history of 
                          bids and auction        
                          transactions.           

  AUC-035                 Only authorised roles   Must
                          shall be able to        
                          control or modify an    
                          auction.                
  -----------------------------------------------------------------------

**Auction transaction rules**

-   Every team starts with the configured initial budget.
-   A bid must be valid against the latest accepted bid at the time it
    is processed.
-   A team cannot bid more than its remaining budget.
-   A team cannot exceed its maximum squad size.
-   A player can have only one successful allocation in the tournament.
-   A sale, budget deduction, and player allocation must succeed or fail
    together.
-   Any manual budget correction must require permission and an audit
    record.
-   The system should prevent or clearly resolve simultaneous bid
    submissions.

### 4.8 Team Roster and Auction Results

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  ROS-001                 The system shall        Must
                          maintain the roster of  
                          players purchased by    
                          each team.              

  ROS-002                 The system shall show   Must
                          each player's purchase  
                          price and auction       
                          status to authorised    
                          users.                  

  ROS-003                 Public visitors shall   Must
                          be able to view         
                          published team rosters  
                          and auction results.    

  ROS-004                 The system shall show   Must
                          each team's initial     
                          budget, spent amount,   
                          and remaining budget    
                          according to            
                          publication settings.   

  ROS-005                 Administrators shall be Must
                          able to correct an      
                          auction allocation only 
                          through a controlled,   
                          audited process.        

  ROS-006                 The system shall        Must
                          prevent the same player 
                          from being actively     
                          assigned to multiple    
                          teams in one            
                          tournament.             
  -----------------------------------------------------------------------

### 4.9 Tournament Format and Fixtures

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  FIX-001                 An administrator shall  Must
                          be able to configure    
                          the tournament format.  

  FIX-002                 The system should       Should
                          support round-robin,    
                          knockout, and           
                          group-stage followed by 
                          knockout formats.       

  FIX-003                 An administrator shall  Must
                          be able to create       
                          team-versus-team        
                          fixtures.               

  FIX-004                 Each team fixture shall Must
                          contain two distinct    
                          participating teams.    

  FIX-005                 A fixture shall support Must
                          a round, group,         
                          scheduled date/time,    
                          venue or court, and     
                          status.                 

  FIX-006                 The administrator shall Must
                          be able to create       
                          individual matches      
                          within a team fixture.  

  FIX-007                 The system shall        Must
                          support configurable    
                          match categories.       

  FIX-008                 The system shall        Must
                          validate that selected  
                          players belong to the   
                          team they represent.    

  FIX-009                 The system shall        Must
                          prevent a player from   
                          being assigned to       
                          overlapping matches,    
                          based on configured     
                          scheduling rules.       

  FIX-010                 The system shall allow  Must
                          fixtures and schedules  
                          to be published to the  
                          public website.         
  -----------------------------------------------------------------------

**Important distinction**

A team fixture is the overall competition between two teams. It consists
of multiple individual matches.

Example:

-   Team fixture: Team A vs Team B
    -   Men's Singles: Player A1 vs Player B1
    -   Women's Singles: Player A2 vs Player B2
    -   Men's Doubles: A3/A4 vs B3/B4
    -   Women's Doubles: A5/A6 vs B5/B6
    -   Mixed Doubles: A1/A2 vs B1/B2

The number and types of individual matches must be configurable. The
example above is illustrative, not a confirmed tournament format.

### 4.10 Match Scoring and Results

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  MAT-001                 The system shall allow  Must
                          an authorised scorer to 
                          view assigned matches.  

  MAT-002                 A scorer shall be able  Must
                          to enter scores game by 
                          game.                   

  MAT-003                 The scoring rules shall Must
                          be configurable,        
                          including points        
                          required to win a game  
                          and games required to   
                          win a match.            

  MAT-004                 The system shall        Must
                          validate scores against 
                          the configured          
                          badminton scoring       
                          rules.                  

  MAT-005                 The system shall        Must
                          calculate the winner of 
                          each game and           
                          individual match.       

  MAT-006                 The system shall allow  Must
                          a match to be saved as  
                          in progress.            

  MAT-007                 The system shall        Must
                          support submission and  
                          confirmation of match   
                          results.                

  MAT-008                 Only confirmed results  Must
                          shall contribute to     
                          official standings and  
                          statistics.             

  MAT-009                 Corrections to          Must
                          confirmed results shall 
                          require authorised      
                          access and an audit     
                          record.                 

  MAT-010                 The system shall        Must
                          display live match      
                          scores to public        
                          visitors when the match 
                          is published.           

  MAT-011                 The system shall        Must
                          calculate the team      
                          fixture result from its 
                          individual match        
                          results.                

  MAT-012                 The system shall        Should
                          support walkovers,      
                          retirements, and other  
                          exceptional outcomes if 
                          enabled by the          
                          administrator.          
  -----------------------------------------------------------------------

### 4.11 Standings and Statistics

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  STA-001                 The system shall        Must
                          calculate team          
                          standings from          
                          confirmed team fixture  
                          results.                

  STA-002                 The administrator shall Must
                          configure points        
                          awarded for fixture     
                          wins, draws, or losses  
                          where applicable.       

  STA-003                 The system shall        Must
                          support configurable    
                          tie-break rules.        

  STA-004                 The system shall        Must
                          display team position,  
                          fixtures played, wins,  
                          losses, and points.     

  STA-005                 The system should       Should
                          display individual      
                          match wins/losses and   
                          game wins/losses.       

  STA-006                 The system should       Should
                          calculate player        
                          statistics such as      
                          matches played, matches 
                          won, games won, and     
                          points scored.          

  STA-007                 Standings and           Must
                          statistics shall be     
                          recalculated when an    
                          authorised result       
                          correction is made.     

  STA-008                 Public standings shall  Must
                          clearly indicate        
                          whether results are     
                          provisional or          
                          confirmed.              
  -----------------------------------------------------------------------

### 4.12 Public Website and Announcements

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  PUB-001                 The public website      Must
                          shall display           
                          tournament information  
                          and branding.           

  PUB-002                 The public website      Must
                          shall provide player    
                          and team registration   
                          forms while             
                          registration is open.   

  PUB-003                 The public website      Must
                          shall show only         
                          approved and published  
                          player and team         
                          profiles.               

  PUB-004                 The public website      Must
                          shall display published 
                          auction results and     
                          team rosters.           

  PUB-005                 The public website      Must
                          shall display fixtures, 
                          schedules, live scores, 
                          results, and standings. 

  PUB-006                 Administrators shall be Should
                          able to publish         
                          announcements.          

  PUB-007                 The website shall be    Must
                          usable on desktop,      
                          tablet, and mobile      
                          devices.                

  PUB-008                 The website shall not   Must
                          expose private contact  
                          details or internal     
                          administrative notes.   
  -----------------------------------------------------------------------

### 4.13 Reports and Audit Logs

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  REP-001                 Administrators shall be Should
                          able to view            
                          registration summaries. 

  REP-002                 Administrators shall be Must
                          able to view auction    
                          expenditure and         
                          remaining budgets.      

  REP-003                 Administrators shall be Must
                          able to view team       
                          rosters and player      
                          allocations.            

  REP-004                 Administrators shall be Should
                          able to view fixture,   
                          match, and standings    
                          reports.                

  REP-005                 Important               Must
                          administrative actions  
                          shall be recorded in an 
                          audit log.              

  REP-006                 Audit entries shall     Must
                          include the actor,      
                          action, timestamp, and  
                          relevant record         
                          reference.              
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 5. Business Rules

  -----------------------------------------------------------------------
  ID                                  Business rule
  ----------------------------------- -----------------------------------
  BR-001                              Public player registration does not
                                      require an account.

  BR-002                              Public team registration does not
                                      require an account.

  BR-003                              A registration is not publicly
                                      visible until approved.

  BR-004                              Only approved and eligible players
                                      may enter the auction.

  BR-005                              A player photograph is required
                                      before player approval.

  BR-006                              A team logo is required before team
                                      approval.

  BR-007                              Skill category is optional and
                                      controlled by the administrator.

  BR-008                              Administrators may create teams
                                      directly without a public
                                      application.

  BR-009                              Team names must be unique within a
                                      tournament.

  BR-010                              All teams receive the same
                                      configured initial auction budget.

  BR-011                              Squad maximum is configurable by
                                      the administrator.

  BR-012                              A player may be allocated to only
                                      one team in a tournament.

  BR-013                              A team may not exceed its budget or
                                      squad limit.

  BR-014                              A team fixture consists of multiple
                                      individual matches, as configured.

  BR-015                              Singles matches require one player
                                      per side; doubles matches require
                                      two players per side.

  BR-016                              Players must be registered to the
                                      team they represent.

  BR-017                              A player must not be scheduled in
                                      conflicting matches.

  BR-018                              Only confirmed results affect
                                      official standings and statistics.

  BR-019                              Historical auction and match
                                      records must be retained when a
                                      player or team is deactivated.

  BR-020                              Files must be verified before being
                                      used as approved public profile
                                      images or logos.
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 6. Data Requirements and Proposed Database Design

The database should use PostgreSQL or another relational database with
strong transaction support. UUID primary keys are recommended.

The following is a logical entity proposal. Exact column definitions,
constraints, indexes, and migrations should be finalised during detailed
design.

### 6.1 Core Entities

  -----------------------------------------------------------------------
  Entity                              Purpose
  ----------------------------------- -----------------------------------
  `tournaments`                       Tournament editions and
                                      configuration.

  `users`                             Authenticated administrative and
                                      operational users.

  `roles` / `user_roles`              Role-based permissions.

  `players`                           Reusable player profiles.

  `player_registrations`              Tournament-specific public player
                                      applications and review status.

  `player_categories`                 Tournament-specific skill
                                      categories.

  `team_registrations`                Public team applications and
                                      approval workflow.

  `teams`                             Approved or administrator-created
                                      tournament teams.

  `team_players`                      Current team-player membership,
                                      including auction allocation
                                      details.

  `auctions`                          Auction sessions.

  `auction_settings`                  Budget, squad size, increments, and
                                      auction rules.

  `auction_players`                   Players listed in an auction.

  `auction_bids`                      Individual bid history.

  `auction_transactions`              Confirmed sold/unsold/withdrawn
                                      outcomes.

  `team_budgets`                      Team budget totals and balances for
                                      an auction.

  `match_categories`                  Singles/doubles categories and
                                      player counts.

  `team_fixtures`                     Team-versus-team fixtures.

  `matches`                           Individual badminton matches within
                                      fixtures.

  `match_players`                     Players assigned to each side of an
                                      individual match.

  `match_games`                       Game-level scores.

  `score_history`                     Audit history of score changes.

  `team_standings`                    Calculated or cached team
                                      standings.

  `player_statistics`                 Calculated or cached player
                                      statistics.

  `file_assets`                       Metadata for images and documents
                                      stored outside the database.

  `announcements`                     Public tournament announcements.

  `audit_logs`                        Important system activity.
  -----------------------------------------------------------------------

### 6.2 Player and Registration Separation

Recommended approach:

-   `players` stores a reusable player profile.
-   `player_registrations` stores the player's application for a
    specific tournament.
-   The public form initially creates a registration record in PENDING
    status.
-   On approval, the administrator may link the registration to an
    existing player or create a new player profile.
-   The registration retains the submitted details and review history.
-   A player can participate in multiple tournament editions without
    duplicating their global identity.

Avoid relying only on a JSON blob for registration data. Store important
fields as typed database columns so they can be validated, searched,
indexed, and reported. A JSON field may be used for optional or
versioned form data if necessary.

### 6.3 Suggested Key Fields

**`players`** - `id` - `full_name` - `date_of_birth` - `gender` -
`email` - `phone` - `address` - `profile_photo_asset_id` -
`created_at` - `updated_at`

**`player_registrations`** - `id` - `tournament_id` - `player_id`
(nullable until linked/approved) - `registration_number` - Submitted
player fields - `profile_photo_asset_id` - `skill_category_id`
(nullable) - `status` - `submitted_at` - `reviewed_by` - `reviewed_at` -
`rejection_reason` - `admin_remarks`

**`player_categories`** - `id` - `tournament_id` - `name` -
`description` - `is_active` - `display_order`

**`team_registrations`** - `id` - `tournament_id` - `team_name` -
`team_logo_asset_id` - `owner_name` - `owner_email` - `owner_phone` -
`status` - `submitted_at` - `reviewed_by` - `reviewed_at` -
`rejection_reason`

**`teams`** - `id` - `tournament_id` - `team_registration_id` (nullable
for admin-created teams) - `name` - `logo_asset_id` - `owner_name` -
`owner_email` - `owner_phone` - `creation_source` (`PUBLIC_REGISTRATION`
or `ADMIN`) - `status` - `created_by` - `created_at`

**`auction_settings`** - `id` - `auction_id` - `initial_team_budget` -
`max_squad_size` - `min_bid_increment` - `allow_reauction` -
`allow_manual_adjustment`

**`team_budgets`** - `id` - `auction_id` - `team_id` -
`initial_budget` - `spent_amount` - `remaining_budget`

**`auction_players`** - `id` - `auction_id` - `player_registration_id` -
`base_price` - `status` - `auction_order` - `final_price` -
`winning_team_id` - `started_at` - `closed_at`

**`auction_bids`** - `id` - `auction_player_id` - `team_id` -
`bid_amount` - `placed_by` - `bid_number` - `created_at` - `status`

**`auction_transactions`** - `id` - `auction_player_id` - `team_id` -
`player_id` - `winning_bid_id` - `final_amount` - `transaction_status` -
`confirmed_by` - `confirmed_at`

**`team_fixtures`** - `id` - `tournament_id` - `round_name` -
`group_id` - `team_a_id` - `team_b_id` - `scheduled_at` - `status` -
`team_a_match_wins` - `team_b_match_wins` - `winning_team_id` -
`confirmed_at`

**`matches`** - `id` - `team_fixture_id` - `match_category_id` -
`court_id` - `match_number` - `scheduled_at` - `status` -
`winner_team_id` - `umpire_user_id`

**`match_players`** - `id` - `match_id` - `team_id` - `player_id` -
`side` (`A` or `B`) - `position`

**`match_games`** - `id` - `match_id` - `game_number` - `team_a_score` -
`team_b_score` - `winner_team_id` - `completed_at`

**`file_assets`** - `id` - `original_filename` - `object_key` -
`bucket_name` - `content_type` - `file_extension` - `file_size` -
`checksum` - `asset_type` - `visibility` - `upload_status` -
`uploaded_by` - `created_at` - `deleted_at`

### 6.4 Data Integrity

-   Use foreign keys for relationships.
-   Add unique constraints for registration numbers and team names
    within a tournament.
-   Add appropriate checks for non-negative bids, scores, budgets, and
    squad limits.
-   Use database transactions for auction sales and budget updates.
-   Prevent duplicate active player allocations within a tournament.
-   Preserve historical records; prefer status changes or soft deletion
    over destructive deletion.
-   Store timestamps consistently, preferably in UTC, and display them
    in the tournament's configured local time zone.

------------------------------------------------------------------------

## 7. File and Image Storage

### 7.1 Storage Approach

Cloudflare R2 is the proposed object storage service for:

-   Player photographs
-   Team logos
-   Tournament logos and banners
-   Optional supporting documents

Binary files should not be stored directly in PostgreSQL. The database
should store file metadata and the object key.

### 7.2 Upload Flow

1.  The frontend requests an upload URL from the backend.
2.  The backend authenticates the request where applicable and generates
    a short-lived presigned upload URL.
3.  The frontend uploads the file directly to object storage.
4.  The frontend informs the backend that the upload has completed.
5.  The backend verifies the object and records its metadata.
6.  The asset is marked VERIFIED before it can be used in an approved
    public profile.

For public registration, the upload process may use a short-lived
registration upload token or another abuse-resistant mechanism because
the applicant is not logged in.

### 7.3 File Validation and Security

-   Allow only explicitly supported formats, such as JPEG, PNG, and WebP
    for images.
-   Validate the actual file content, not only the extension or
    browser-provided MIME type.
-   Enforce configurable maximum file sizes and image dimensions.
-   Generate unique object keys; do not trust user-supplied filenames as
    storage keys.
-   Keep storage credentials on the backend.
-   Use private storage by default and publish only approved assets.
-   Keep sensitive supporting documents private.
-   Consider image resizing and thumbnails for public profile pages.
-   Remove abandoned temporary uploads through a scheduled cleanup
    process.
-   Apply rate limits and anti-spam controls to public upload endpoints.

### 7.4 Example Object Key Layout

``` text
badminton-tournament/
  tournaments/{tournament_id}/
    branding/tournament-logo.png
    players/{player_id}/profile-photo.webp
    teams/{team_id}/team-logo.png
    documents/{registration_id}/supporting-document.pdf
```

The object key should be stored in `file_assets`. Public URLs should be
generated through the configured public delivery domain or a controlled
backend endpoint.

------------------------------------------------------------------------

## 8. External Interfaces

### 8.1 User Interface

-   Responsive web interface.
-   Public pages accessible without login.
-   Protected administrative dashboards.
-   Dedicated auctioneer interface suitable for a large display.
-   Scoring interface suitable for tablets and mobile devices.
-   Clear status indicators for pending, approved, rejected, live,
    completed, and confirmed records.

### 8.2 Backend API

The backend should expose REST APIs documented using OpenAPI/Swagger.

Example API groups:

-   `/api/public/tournaments`
-   `/api/public/player-registrations`
-   `/api/public/team-registrations`
-   `/api/admin/players`
-   `/api/admin/teams`
-   `/api/admin/registrations`
-   `/api/admin/auction-settings`
-   `/api/auction`
-   `/api/fixtures`
-   `/api/matches`
-   `/api/matches/{id}/scores`
-   `/api/standings`
-   `/api/announcements`
-   `/api/files`

Exact endpoints and request/response schemas will be defined during API
design.

### 8.3 Real-Time Communication

WebSockets or a comparable real-time mechanism should be used for:

-   Auction state and bid updates.
-   Current player and auction status.
-   Live match score updates.
-   Public display refreshes.

The server remains the source of truth. Clients must not independently
decide whether a bid or score update is valid.

------------------------------------------------------------------------

## 9. Non-Functional Requirements

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  NFR-001                 The public website and  Must
                          admin portal shall be   
                          responsive on common    
                          desktop, tablet, and    
                          mobile screen sizes.    

  NFR-002                 The system shall        Must
                          enforce authentication  
                          and role-based          
                          authorisation for       
                          protected functions.    

  NFR-003                 All network traffic     Must
                          shall use HTTPS in      
                          production.             

  NFR-004                 Passwords shall be      Must
                          stored using a modern   
                          adaptive password       
                          hashing algorithm.      

  NFR-005                 The system shall        Must
                          validate and sanitise   
                          all user input on the   
                          backend.                

  NFR-006                 The system shall        Must
                          protect public forms    
                          against spam, abuse,    
                          and automated           
                          submissions.            

  NFR-007                 Auction operations      Must
                          shall be                
                          transaction-safe and    
                          resistant to concurrent 
                          bid conflicts.          

  NFR-008                 The system shall        Must
                          maintain audit records  
                          for sensitive           
                          administrative and      
                          financial-like auction  
                          operations.             

  NFR-009                 The system shall        Must
                          support database backup 
                          and restoration         
                          procedures.             

  NFR-010                 The system should       Should
                          provide useful          
                          application logs and    
                          error monitoring.       

  NFR-011                 The system should be    Should
                          designed so that future 
                          tournament editions can 
                          be added without major  
                          redesign.               

  NFR-012                 The system should use   Should
                          pagination and          
                          filtering for large     
                          lists.                  

  NFR-013                 Public pages should be  Should
                          optimised for           
                          reasonable loading      
                          times on typical mobile 
                          connections.            

  NFR-014                 The system shall avoid  Must
                          exposing private        
                          contact details,        
                          credentials, or         
                          internal notes through  
                          public APIs.            

  NFR-015                 The system shall have a Should
                          documented deployment   
                          and recovery process.   
  -----------------------------------------------------------------------

### 9.1 Privacy

-   Publish only fields explicitly intended for public display.
-   Do not expose emergency contact details, private addresses, internal
    notes, or administrative user information.
-   Provide an appropriate privacy notice and applicant consent before
    collecting personal data.
-   Define a retention policy for rejected registrations and supporting
    documents.
-   Restrict access to personal information to authorised roles.
-   Review applicable Sri Lankan data protection requirements before
    production deployment.

### 9.2 Reliability and Recovery

-   Back up the relational database regularly.
-   Test restoration, not only backup creation.
-   Keep an audit history for auction and result corrections.
-   Design auction sale processing to avoid partial updates.
-   Provide a documented manual recovery procedure for an interrupted
    auction or scoring session.

------------------------------------------------------------------------

## 10. Proposed Technology Stack

The following is a recommended starting point and may be changed during
technical design.

  -----------------------------------------------------------------------
  Layer                   Proposed technology     Reason
  ----------------------- ----------------------- -----------------------
  Frontend                React with Next.js      Responsive public and
                                                  administrative web
                                                  interfaces.

  Backend                 Java with Spring Boot   Mature REST API,
                                                  security, validation,
                                                  and transaction
                                                  support.

  Database                PostgreSQL              Relational data
                                                  integrity and
                                                  transactional
                                                  operations.

  Real-time               Spring WebSocket        Live auction and
                                                  scoring updates.

  Authentication          Spring Security with    Protected
                          JWT or secure           administrative access.
                          session-based           
                          authentication          

  Object storage          Cloudflare R2           Player photos, team
                                                  logos, and documents.

  API documentation       OpenAPI / Swagger       API documentation and
                                                  testing.

  Deployment              Cloud hosting /         Repeatable deployment
                          containerised           and scaling.
                          deployment              
  -----------------------------------------------------------------------

The architecture should be modular. A single deployable backend with
clear modules is likely sufficient for the first edition; microservices
are not required unless scale or operational needs justify them.

------------------------------------------------------------------------

## 11. Main Workflows

### 11.1 Player Registration

``` text
Applicant opens registration page
        |
Completes form and uploads photograph
        |
Backend validates form and upload
        |
Registration created as PENDING
        |
Registration manager reviews application
        |
   +----+----+
   |         |
Approve    Reject
   |         |
Create or   Store reason
link player and notify applicant
profile
   |
Player becomes eligible for publication
and, subject to auction rules, auction
```

### 11.2 Team Registration

``` text
Applicant opens team registration page
        |
Enters team details and uploads logo
        |
Backend validates form and upload
        |
Team application created as PENDING
        |
Administrator reviews application
        |
   +----+----+
   |         |
Approve    Reject
   |         |
Create     Store reason
team       and notify applicant
   |
Team becomes eligible for tournament
```

Administrators may also create a team directly from the admin portal.
Such a team is marked as ADMIN-created and does not require a public
application.

### 11.3 Auction

``` text
Administrator configures auction
        |
System creates equal team budgets
        |
Auctioneer starts auction
        |
Select eligible player
        |
Teams place bids
        |
Backend validates each bid
        |
Auctioneer closes player as SOLD / UNSOLD / WITHDRAWN
        |
If SOLD: record transaction, allocate player,
deduct budget, and update roster atomically
        |
Advance to next player
```

### 11.4 Match and Standings

``` text
Administrator creates team fixture
        |
Adds individual matches and participants
        |
Scorer enters game scores
        |
System validates and calculates match result
        |
Result submitted and confirmed
        |
System updates team fixture result
        |
System recalculates standings and statistics
        |
Public website displays published result
```

------------------------------------------------------------------------

## 12. Security and Permission Considerations

-   Public registration endpoints shall not provide access to
    administrative records.
-   Administrative endpoints shall enforce role and permission checks.
-   Auctioneer permissions shall be limited to auction operations.
-   Scorers shall only update matches assigned to them, unless granted
    broader permission.
-   Team managers, if enabled, shall only view and manage permitted
    information for their own team.
-   File upload URLs shall be short-lived and scoped to a specific
    upload.
-   Sensitive actions shall be logged.
-   Secrets and storage credentials shall be held in
    environment-specific secret management, not source control.
-   Rate limits should be applied to public forms, login attempts, and
    upload endpoints.

------------------------------------------------------------------------

## 13. Acceptance Criteria

The first release shall be considered functionally acceptable when the
following can be demonstrated:

1.  An applicant can submit a player registration without an account.
2.  An applicant can submit a team registration without an account.
3.  Registration managers can approve and reject both types of
    application.
4.  Unapproved players and teams are not shown publicly and cannot enter
    the auction.
5.  Administrators can create teams directly.
6.  Administrators can manage optional player skill categories.
7.  Administrators can configure equal initial team budgets and a
    maximum squad size.
8.  The auctioneer can conduct an auction and the public display updates
    live.
9.  Invalid bids are rejected by the backend.
10. A successful sale updates the player allocation, team roster, and
    budget consistently.
11. Administrators can create team fixtures and individual matches.
12. Scorers can enter game scores and submit results.
13. Only confirmed results update official standings.
14. Public visitors can view published players, teams, auction results,
    fixtures, scores, and standings.
15. Private applicant and administrative information is not exposed
    publicly.
16. Important auction, approval, and result changes are auditable.

------------------------------------------------------------------------

## 14. Items Requiring Confirmation

The following tournament-specific decisions have not yet been confirmed.
They should be agreed before finalising the detailed database schema and
implementation.

  -----------------------------------------------------------------------
  Item                    Current status          Decision needed
  ----------------------- ----------------------- -----------------------
  Number of teams         To be confirmed         e.g. 4, 6, 8, 10, or
                                                  another number

  Maximum players per     Admin configurable;     Confirm the expected
  team                    initial default not     default
                          decided                 

  Match categories        Configurable; exact     Men's Singles, Women's
                          categories not          Singles, Men's Doubles,
                          confirmed               Women's Doubles, Mixed
                                                  Doubles, or a subset

  Tournament format       Not confirmed           Round robin, knockout,
                                                  group stage then
                                                  knockout, or another
                                                  format

  Fixture composition     Not confirmed           Number and categories
                                                  of individual matches
                                                  in each team fixture

  Scoring rules           Configurable; exact     Points per game, games
                          rules not confirmed     per match, and
                                                  applicable badminton
                                                  rules

  Tie-break rules         Not confirmed           Order of points,
                                                  fixture wins, match
                                                  difference, game
                                                  difference, points
                                                  difference,
                                                  head-to-head, etc.

  Auction currency        Not confirmed           Currency and display
                                                  format

  Auction values          Not confirmed           Initial budget, base
                                                  price, and minimum bid
                                                  increment

  Team manager accounts   Optional                Whether team contacts
                                                  need login access

  Public contact details  Not confirmed           Which player/team
                                                  fields should be
                                                  publicly visible

  Result approval         Proposed confirmation   Whether scorer
                          workflow                submission requires a
                                                  second official/admin
                                                  approval

  Venue and court setup   Not confirmed           Whether multiple
                                                  venues/courts and court
                                                  allocation are required

  Notifications           Proposed                Email, SMS, WhatsApp,
                                                  or in-app notifications
  -----------------------------------------------------------------------

These values should be configurable where practical rather than
hard-coded.

------------------------------------------------------------------------

## 15. Suggested Implementation Phases

### Phase 1 --- Foundation and Registration

-   Project setup and deployment pipeline.
-   Authentication, roles, and permissions.
-   Tournament configuration.
-   Player and team public registration.
-   File upload integration.
-   Admin approval workflows.
-   Player and team directories.

### Phase 2 --- Auction

-   Auction configuration.
-   Team budget initialisation.
-   Player auction queue.
-   Auctioneer console.
-   Bid validation and transaction handling.
-   Live public auction display.
-   Team roster and budget views.

### Phase 3 --- Fixtures and Scoring

-   Tournament format configuration.
-   Fixture generation and management.
-   Match categories and player assignment.
-   Scorer interface.
-   Game-by-game scoring.
-   Result confirmation and correction workflow.

### Phase 4 --- Public Results and Reporting

-   Public fixtures and schedules.
-   Live scores.
-   Standings and player statistics.
-   Announcements.
-   Administrative reports.
-   Audit and operational improvements.

### Phase 5 --- Hardening and Launch

-   Security review.
-   Load and concurrency testing, especially for the auction.
-   Backup and restore testing.
-   Mobile and browser testing.
-   User acceptance testing.
-   Production deployment and operational handover.

------------------------------------------------------------------------

## 16. Out of Scope for the Initial Release

Unless separately approved, the following are not required for the first
release:

-   Native Android or iOS applications.
-   Online payment processing.
-   Automated player ranking based on external badminton databases.
-   AI-based player evaluation or team selection.
-   Streaming video.
-   Integration with external tournament or ranking platforms.
-   Automated referee hardware or court sensor integration.

These may be considered for future versions.

------------------------------------------------------------------------

## 17. Conclusion

This SRS defines a configurable, web-based platform for managing a
team-based badminton tournament. It covers public registration,
approval, team creation, live player auctions, team budgets, fixtures,
individual match scoring, standings, and public tournament information.

The most important architectural distinction is that a **team fixture**
contains multiple **individual badminton matches**, and each individual
match contains one or more **games**. Keeping these concepts separate
will make the system flexible enough to support different tournament
formats and match-category combinations.

The remaining tournament-specific decisions in Section 14 should be
confirmed before the detailed ERD, API contract, and implementation
backlog are finalised.

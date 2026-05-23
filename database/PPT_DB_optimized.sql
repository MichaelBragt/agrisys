/*
   Agrisys PPT Database Installation Script
   Beskrivelse: Opretter database og tabeller med fuld transactionstyring.
   Hvis én del fejler, rulles alt tilbage (Atomic operation).
*/

-- 1. Opret Databasen hvis den ikke findes
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'agrisys_ppt')
BEGIN
    CREATE DATABASE agrisys_ppt;
    PRINT 'Database agrisys_ppt oprettet.';
END
GO

-- Skift kontekst til den nye database
USE agrisys_ppt;
GO

-- 2. Transaction med Try/Catch for sikker tabeloprettelse
BEGIN TRANSACTION;
BEGIN TRY

    -- Slet tabeller i omvendt rækkefølge hvis de findes (valgfrit - fjern hvis data skal bevares)
    -- DROP TABLE IF EXISTS Measurement, Placement, Tagging, Pig, RFID_Tag, Pen, AppUser;

    -- A. Brugerstyring
    CREATE TABLE AppUser (
        user_id INT PRIMARY KEY IDENTITY(1,1),
        username VARCHAR(50) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        user_role VARCHAR(20) NOT NULL CHECK (user_role IN ('Landmand', 'Raadgiver'))
    );

    -- B. Location data as PPT device number
    CREATE TABLE Location (
        location_id INT PRIMARY KEY IDENTITY(1,1),
        location_name VARCHAR(50) NOT NULL
    );

    -- C. Det fysiske udstyr (Lagerliste)
    CREATE TABLE Responders (
        responder_id VARCHAR(50) PRIMARY KEY,
        status VARCHAR(50) DEFAULT 'I brug' CHECK (status IN ('I brug', 'Ledig', 'Defekt'))
    );

    -- D. Den biologiske gris
    CREATE TABLE Pig (
        animal_number VARCHAR(20) PRIMARY KEY,
        birth_date DATE,
        status VARCHAR(20) DEFAULT 'Aktiv' CHECK (status IN ('Aktiv', 'Slagtet', 'Syg'))
    );

    -- E. Koblingstabel: Gris <-> RFID Tag (Historik over hvilket tag sidder på hvilken gris)
    CREATE TABLE Responder_Assignment (
        assignment_id INT PRIMARY KEY IDENTITY(1,1),
        animal_number VARCHAR(20),
        responder_id VARCHAR(50),
        date_assigned DATETIME DEFAULT GETUTCDATE(),
        date_removed DATETIME NULL, -- NULL betyder tagget er aktivt nu
        CONSTRAINT FK_Responder_Assignment_Pig FOREIGN KEY (animal_number) REFERENCES Pig(animal_number),
        CONSTRAINT FK_Responder_Assignment_Responders FOREIGN KEY (responder_id) REFERENCES Responders(responder_id)
    );

    -- F. Koblingstabel: Gris <-> Lokation (Sporbarhed/Logistik)
    CREATE TABLE Pig_Location (
        pig_location_id INT PRIMARY KEY IDENTITY(1,1),
        animal_number VARCHAR(20) NOT NULL,
        location_id INT NOT NULL,
        arrived_at DATETIME DEFAULT GETUTCDATE(),
        departed_at DATETIME NULL,
        CONSTRAINT FK_Pig_Location_Pig FOREIGN KEY (animal_number) REFERENCES Pig(animal_number),
        CONSTRAINT FK_Pig_Location_Location FOREIGN KEY (location_id) REFERENCES Location(location_id)
    );

    -- G. Sensordata (PPT-målingerne)
    CREATE TABLE PPT_Data (
        ppt_data_id INT PRIMARY KEY IDENTITY(1,1),
        assignment_id INT NOT NULL, -- Refererer til den specifikke grise-tag-kombination
        visit_time DATETIME NOT NULL DEFAULT GETUTCDATE(),
        pig_weight DECIMAL(10,3),
        feed_intake DECIMAL(10,3),
        visit_duration INT, -- Tid i sekunder
        CONSTRAINT FK_PPT_Data_Responder_Assignment FOREIGN KEY (assignment_id) REFERENCES Responder_Assignment(assignment_id)
    );

    -- 3. Performance Indexing
    CREATE INDEX IX_PPT_Data_visit_time ON PPT_Data(visit_time);

    -- Filtreret index for hurtig opslag af aktive tags (Best practice for historik-tabeller)
    -- ÆNDRET: Tilføjet UNIQUE for at sikre at én responder kun kan være aktiv på én gris ad gangen.
    CREATE UNIQUE INDEX UIX_Responder_Assignment_Active_Responder ON Responder_Assignment(responder_id) WHERE date_removed IS NULL;

    -- Hurtigt opslag på grise der ikke er flyttet endnu (nuværende lokation)
    CREATE INDEX IX_Pig_Location_Current ON Pig_Location(animal_number, location_id)
    WHERE departed_at IS NULL;

    -- =========================================================================
    -- INDSÆT TESTBRUGERE MED SHA-256 HASHING
    -- =========================================================================
    PRINT 'Indsætter pre-hashed testbrugere...';

    INSERT INTO AppUser (username, password, user_role) VALUES 
    (
        'Landmand', 
        LOWER(sys.fn_varbintohexstr(HASHBYTES('SHA2_256', '1234'))), 
        'Landmand'
    ),
    (
        'Rådgiver', 
        LOWER(sys.fn_varbintohexstr(HASHBYTES('SHA2_256', '5678'))), 
        'Raadgiver' -- Matcher CHECK (user_role IN ('Landmand', 'Raadgiver'))
    );
    
    -- =========================================================================

    -- Hvis vi når hertil uden fejl, gemmes alle ændringer
    COMMIT TRANSACTION;
    PRINT 'Database-struktur oprettet succesfuldt via transaction.';

END TRY
BEGIN CATCH
    -- Hvis der opstår en fejl, rulles alt tilbage til før BEGIN TRANSACTION
    ROLLBACK TRANSACTION;

    -- Fejlbesked til debug
    PRINT 'FEJL: Transaction fejlede. Ingen ændringer blev udført.';
    SELECT
        ERROR_NUMBER() AS ErrorNumber,
        ERROR_MESSAGE() AS ErrorMessage,
        ERROR_LINE() AS ErrorLine;
END CATCH;
GO

-- =========================================================================
-- 4. OPRETTELSE AF DEDIKERET APP-BRUGER OG DB_OWNER RETTIGHEDER
-- =========================================================================
USE agrisys_ppt;
GO

-- A. Opret login på selve SQL Serveren (hvis det ikke allerede findes)
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'agrisysUser')
BEGIN
    CREATE LOGIN agrisysUser WITH PASSWORD = 'agrisysUser', DEFAULT_DATABASE = agrisys_ppt;
    PRINT 'Server Login agrisysUser oprettet.';
END
GO

-- B. Opret brugeren i denne specifikke database knyttet til loginnet
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'agrisysUser')
BEGIN
    CREATE USER agrisysUser FOR LOGIN agrisysUser;
    PRINT 'Database User agrisysUser oprettet i agrisys_ppt.';
END
GO

-- C. Giv brugeren db_owner rollen, så applikationen kan læse/skrive alt i denne DB
ALTER ROLE db_owner ADD MEMBER agrisysUser;
PRINT 'agrisysUser er nu tildelt db_owner rettigheder.';
GO
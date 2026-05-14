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
        UserID INT PRIMARY KEY IDENTITY(1,1),
        Username VARCHAR(50) NOT NULL UNIQUE,
        PasswordHash VARCHAR(255) NOT NULL,
        UserRole VARCHAR(20) NOT NULL CHECK (UserRole IN ('Landmand', 'Raadgiver'))
    );

    -- B. Lokationsstamdata
    CREATE TABLE Pen (
        PenID INT PRIMARY KEY IDENTITY(1,1),
        PenName VARCHAR(50) NOT NULL
    );

    -- C. Det fysiske udstyr (Lagerliste)
    CREATE TABLE RFID_Tag (
        RFID_TagID INT PRIMARY KEY IDENTITY(1,1),
        RFIDCode VARCHAR(50) NOT NULL UNIQUE
    );

    -- D. Den biologiske gris
    CREATE TABLE Pig (
        PigID INT PRIMARY KEY IDENTITY(1,1),
        BirthDate DATE,
        Status VARCHAR(20) DEFAULT 'Aktiv' CHECK (Status IN ('Aktiv', 'Slagtet', 'Syg'))
    );

    -- E. Koblingstabel: Gris <-> RFID Tag (Historik over hvilket tag sidder på hvilken gris)
    CREATE TABLE Tagging (
        TaggingID INT PRIMARY KEY IDENTITY(1,1),
        PigID INT NOT NULL,
        TagID INT NOT NULL,
        DateAssigned DATETIME DEFAULT GETUTCDATE(),
        DateRemoved DATETIME NULL, -- NULL betyder tagget er aktivt nu
        CONSTRAINT FK_Tagging_Pig FOREIGN KEY (PigID) REFERENCES Pig(PigID),
        CONSTRAINT FK_Tagging_Tag FOREIGN KEY (TagID) REFERENCES RFID_Tag(RFID_TagID)
    );

    -- F. Koblingstabel: Gris <-> Lokation (Sporbarhed/Logistik)
    CREATE TABLE Placement (
        PlacementID INT PRIMARY KEY IDENTITY(1,1),
        PigID INT NOT NULL,
        PenID INT NOT NULL,
        ArrivedAt DATETIME DEFAULT GETDATE(),
        DepartedAt DATETIME NULL,
        CONSTRAINT FK_Placement_Pig FOREIGN KEY (PigID) REFERENCES Pig(PigID),
        CONSTRAINT FK_Placement_Pen FOREIGN KEY (PenID) REFERENCES Pen(PenID)
    );

    -- G. Sensordata (PPT-målingerne)
    CREATE TABLE Measurement (
        MeasurementID INT PRIMARY KEY IDENTITY(1,1),
        TaggingID INT NOT NULL, -- Refererer til den specifikke grise-tag-kombination
        Timestamp DATETIME NOT NULL DEFAULT GETDATE(),
        PigWeight DECIMAL(10,3),
        FeedIntake DECIMAL(10,3),
        VisitDuration INT, -- Tid i sekunder
        CONSTRAINT FK_Measurement_Tagging FOREIGN KEY (TaggingID) REFERENCES Tagging(TaggingID)
    );

    -- 3. Performance Indexing
    CREATE INDEX IX_Measurement_Timestamp ON Measurement(Timestamp);

    -- Filtreret index for hurtig opslag af aktive tags (Best practice for historik-tabeller)
    CREATE INDEX IX_Tagging_Active ON Tagging(TagID) WHERE DateRemoved IS NULL;

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
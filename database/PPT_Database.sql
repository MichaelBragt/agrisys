/* Agrisys PPT Database Script
*/

-- 1. Brugerstyring
CREATE TABLE AppUser (
    UserID INT PRIMARY KEY IDENTITY(1,1),
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL,
    UserRole VARCHAR(20) NOT NULL CHECK (UserRole IN ('Landmand', 'Raadgiver'))
);

-- 2. Lokationsstamdata
CREATE TABLE Pen (
    PenID INT PRIMARY KEY IDENTITY(1,1),
    PenName VARCHAR(50) NOT NULL, -- f.eks. 'Sti 102'
);

-- 3. Det fysiske udstyr (Lagerliste)
CREATE TABLE RFID_Tag (
    RFID_TagID INT PRIMARY KEY IDENTITY(1,1),
    RFIDCode VARCHAR(50) NOT NULL UNIQUE -- Den fysiske kode fra chippen
);

-- 4. Den biologiske gris
CREATE TABLE Pig (
    PigID INT PRIMARY KEY IDENTITY(1,1),
    BirthDate DATE,
    Status VARCHAR(20) DEFAULT 'Aktiv' CHECK (Status IN ('Aktiv', 'Slagtet', 'Syg'))
);

-- 5. Koblingstabel: Gris <-> RFID Tag (Muliggør genbrug)
CREATE TABLE Tagging (
    TaggingID INT PRIMARY KEY IDENTITY(1,1),
    PigID INT FOREIGN KEY REFERENCES Pig(PigID),
    TagID INT FOREIGN KEY REFERENCES RFID_Tag(RFID_TagID),
    DateAssigned DATETIME DEFAULT GETDATE(),
    DateRemoved DATETIME NULL -- NULL betyder at tagget er aktivt på grisen lige nu
);

-- 6. Koblingstabel: Gris <-> Lokation (Sporbarhed)
CREATE TABLE Placement (
    PlacementID INT PRIMARY KEY IDENTITY(1,1),
    PigID INT FOREIGN KEY REFERENCES Pig(PigID),
    PenID INT FOREIGN KEY REFERENCES Pen(PenID),
    ArrivedAt DATETIME DEFAULT GETDATE(),
    DepartedAt DATETIME NULL
);

-- 7. Sensordata (PPT-målingerne)
CREATE TABLE Measurement (
    MeasurementID INT PRIMARY KEY IDENTITY(1,1),
    TaggingID INT FOREIGN KEY REFERENCES Tagging(TaggingID), -- Kobler til specifik gris/tag periode
    Timestamp DATETIME NOT NULL,
    PigWeight DECIMAL(10,3),
    FeedIntake DECIMAL(10,3),
    VisitDuration INT -- Tid i sekunder
);

-- 8. Performance Indexing (For hurtige grafer/dashboards)
CREATE INDEX IX_Measurement_Timestamp ON Measurement(Timestamp);
CREATE INDEX IX_Tagging_Active ON Tagging(TagID) WHERE DateRemoved IS NULL;
# MapleDeadlockTracker

**Head developer**: Ronan C. P. Lana

## About

It's a developer tool designed to pick and line potential deadlock issues throughout a Java project.

## How to use

* Install the Tracker folder in the __same directory of the target project__.
* Then, build and run.
* The result of the search should be found in the console.

Contents such as code entry points and source directory (and subdirectories) to search are configured within the __config.cfg__ file.

## Observations

* This program does a static review over the source code. Object interactions are not treated dynamically, here.
* Script calls are noted as potential deadlock issuers if not treated properly. That so, the program informs when a given script handler (e.g., Invocable) runs scripts holding locks.
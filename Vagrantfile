# coding: utf-8
#
###
#
# = Why does this file exist?
#
# The notification-worker-lambdas are defined as Docker images, which
# are deployed to AWS Lambdas. Within these Docker images, they are
# just regular scala-based lambdas, so it is not neccessary to do
# anything with Docker to just run the lambda code locally. All of the
# building and deployment of the docker images is done automatically
# as part of the teamcity and riffraff stages of the development
# process.
#
# However, if you need to explicitly debug or test something related
# to the Docker part of the build process (as opposed to the Scala
# code itself) then you may to run Docker.
#
# The main problem with this is that Docker is a Linux app and most of
# us develop on Macs. One solution to this is the Docker Desktop [1].
# It works great -- it basically creates an invisible virtual machine
# running Linux and exposes the Docker toolset that way -- but it is
# now a commercial product, and is not free for commercial use.
#
# As an alternative, this file allows us to use Vagrant to set-up a
# working environment which will build the Docker poritions of the
# networking lambdas without requiring any non-free
# software. Naturally it does this using essentially the same process,
# which is to create an (almost) invisible VM which runs Linux and
# docker within it.
#
# = How to use it.
#
#    $ brew install vagrant
#    $ vagrant up
#
# Now you can run:
#
#    $ vagrant ssh
#
# to actually 'connect' to the vm, and you can run sbt there as
# normal:
#
#    TODO
#
# Optionally, once you are finished with it, you can run:
#
#    $ vagrant destroy
#
# to delete the image from your machine (which is fine because it can
# easily be recreated as needed; no useful information is stored
# within the box itself).
#
###

# We are going to create a very simple image based on the minimial
# Linux variant Alpine linux. This keeps the resource consumption low.

sbt_version = "1.6.2"

Vagrant.configure("2") do |config|
  config.vm.box = "generic/alpine38"
  config.vm.hostname = "mobile-n10n.box"

  # having set up the base alpine linux os, we install docker:

  config.vm.provision "shell", inline: "apk add docker curl openjdk8; curl -sLO 'https://github.com/sbt/sbt/releases/download/v1.6.2/sbt-#{sbt_version}.zip'; unzip sbt-#{sbt_version}.zip"

  config.vm.synced_folder "./", "/src"

  # config.vm.network :private_network, ip: "192.168.0.42"
end

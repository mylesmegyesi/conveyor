
def exec_in(dir, command)
  sh "cd #{dir} && #{command}"
end

def clean(dir)
  rm_script = 'rm -rf lib target pom.xml .lein-deps-sum .sass-cache checkouts'
  lein_task(dir, 'clean')
  exec_in(dir, rm_script)
  sh rm_script
end

def deps(dir)
  lein_task(dir, 'deps')
end

def jar_spec(dir)
  lein_task(dir, 'with-profile dev uberjar')
  exec_in(dir, "java -jar target/#{dir}-0.1.1-standalone.jar")
end

def spec(dir)
  lein_task(dir, 'spec')
end

def _install(dir)
  lein_task(dir, 'install')
end

def ci?
  ENV['CI'] == '1'
end

def lein_bin
  ci? ? 'lein2' : 'lein'
end

def lein_task(dir, task)
  exec_in(dir, "#{lein_bin} #{task}")
end

def checkouts(client, servers)
  Dir.mkdir "#{client}/checkouts" unless File.exists?("#{client}/checkouts")
  servers.each do |server|
    ln_path = "#{client}/checkouts/#{server}"
    if !(File.exists?(ln_path))
      sh "ln -s #{File.expand_path(File.dirname(__FILE__))}/#{server} #{client}/checkouts/#{server}"
    end
  end
end

def package(name, dependencies)
  desc "Clean #{name}"
  task :clean do
    clean(name)
  end

  desc "Gather dependencies for #{name}"
  task :deps do
    checkouts(name, dependencies) unless ci?
    dependencies.each do |dep|
      _install(dep)
    end
  end

  desc "Install #{name}"
  task :install do
    _install(name)
  end

  desc "Run #{name} specs"
  task :spec => [:clean, :deps] do
    spec(name)
    jar_spec(name)
  end

  desc "Setup checkouts for #{name}"
  task :checkouts do
    checkouts(name, dependencies)
  end
end

namespace :conveyor do
  package('conveyor', [])
end

namespace 'conveyor-sass' do
  package('conveyor-sass', %w{conveyor})
end

namespace 'conveyor-compass' do
  package('conveyor-compass', %w{conveyor conveyor-sass})
end

namespace 'conveyor-coffeescript' do
  package('conveyor-coffeescript', %w{conveyor})
end

PROJECTS = %w(conveyor conveyor-sass conveyor-compass conveyor-coffeescript)

def create_task_for_all(task_name)
  task task_name => PROJECTS.map {|project| "#{project}:#{task_name}"}
end

desc 'Setup checkouts for subprojects'
create_task_for_all(:checkouts)

desc 'Run the specs for conveyor'
create_task_for_all(:spec)

desc 'Clean all conveyor projects'
create_task_for_all(:clean)

desc 'Install all conveyor projects'
create_task_for_all(:install)

task :default => :spec


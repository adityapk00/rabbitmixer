const gulp = require('gulp');
const browserify = require('browserify');
const watchify = require('watchify');
const errorify = require('errorify');
const del = require('del');
const tsify = require('tsify');
const gulpTypings = require('gulp-typings');
const source = require('vinyl-source-stream');
const runSequence = require('run-sequence');
const webserver = require('gulp-webserver');
const util = require('gulp-util');


function createBrowserifier(entry) {
    return browserify({
        basedir: '.',
        debug: !util.env.production,
        entries: [entry],
        cache: {},
        packageCache: {}
    })
    .plugin(tsify, { noImplicitAny: false })
    .plugin(watchify)
    .plugin(errorify);
}

function bundle(browserifier, bundleName, destination) {
    return browserifier
        .bundle()
        .pipe(source(bundleName))
        .pipe(gulp.dest(destination));
}

gulp.task('clean', () => {
    return del('./javascript/**/*')
});

gulp.task('installTypings', () => {
    return gulp.src('typings.json').pipe(gulpTypings());
});

gulp.task('tsc-browserify-src', () => {
    return bundle(
        createBrowserifier('./index.ts'),
        'bundle.js',
        'javascript');
});

gulp.task('webserver', function() {
    gulp.src('.')
      .pipe(webserver({
        livereload: true,
        directoryListing: true,
        open: true,
        port: 8080
      }));
  });

gulp.task('default', (done) => {
    runSequence(['clean', 'installTypings'], 'tsc-browserify-src', 'webserver', () => {
            console.log('Watching...')
            gulp.watch(['./**/*.ts'], 
                       ['tsc-browserify-src']);		
    });
});
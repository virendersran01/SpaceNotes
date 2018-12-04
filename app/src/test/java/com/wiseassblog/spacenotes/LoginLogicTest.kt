package com.wiseassblog.spacenotes

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.tasks.Task
import com.wiseassblog.domain.DispatcherProvider
import com.wiseassblog.domain.ServiceLocator
import com.wiseassblog.domain.domainmodel.Result
import com.wiseassblog.domain.domainmodel.User
import com.wiseassblog.domain.error.SpaceNotesError
import com.wiseassblog.domain.interactor.AuthSource
import com.wiseassblog.spacenotes.login.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * Philipp Hauer
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class LoginLogicTest {


    private val dispatcher: DispatcherProvider = mockk()

    private val locator: ServiceLocator = mockk()

    private val view: ILoginContract.View = mockk(relaxed = true)

    private val task: Task<GoogleSignInResult> = mockk()

    private val auth: AuthSource = mockk()

    private val testAccount: GoogleSignInAccount = mockk()


    private lateinit var logic: LoginLogic

    val testIdToken: String = "8675309"


    fun getUser(uid: String = "8675309",
                name: String = "Ajahn Chah",
                profilePicUrl: String = ""
    ) = User(uid,
            name,
            profilePicUrl)

    @Before
    fun clear() {
        clearMocks()

        logic = LoginLogic(dispatcher, locator, view, auth)

    }

    /**
     * In onstart, we ask firebase auth for the current user object. Based on that result we render
     * the ui appropriately.
     * a. User is retrieved successfully
     * b. User is null
     * c. Exception: no network connectivity
     *
     * a:
     * 1. Check Network status: available
     * 2. Ask auth source for current user: User
     * 3. Start antenna loop
     * 4. set login status: "Signed In"
     * 5. set login button: "SIGN OUT"
     *
     */
    @Test
    fun `On Start retrieve User`() = runBlocking {

        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        coEvery {
            auth.getCurrentUser(locator)
        } returns Result.build { getUser() }

        logic.event(LoginEvent.OnStart)


        coVerify { auth.getCurrentUser(locator) }
        verify { view.setLoginStatus(SIGNED_IN) }
        verify { view.showLoopAnimation() }
        verify { view.setAuthButton(SIGN_OUT) }
    }

    /**
     *b:
     * 1. Check Network status: available
     * 2. Ask auth source for current user: null
     * 3. Set animation to antenna_full
     * 4. set login status: "Signed Out"
     * 5. set login button: "SIGN IN"
     */
    @Test
    fun `On Start retrieve null`() = runBlocking {

        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        coEvery {
            auth.getCurrentUser(locator)
        } returns Result.build { null }

        logic.event(LoginEvent.OnStart)


        coVerify { auth.getCurrentUser(locator) }
        verify { view.setLoginStatus(SIGNED_OUT) }
        verify { view.setStatusDrawable(ANTENNA_FULL) }
        verify { view.setAuthButton(SIGN_IN) }
    }

    /**
     *c:
     * 1. Check network status: unavailable
     * 2. set animatin to drawable antenna_empty:
     * 3. set login status: "Network Unavailable"
     * 4. set login button: "RETRY"
     */
    @Test
    fun `On Start retrieve network error`() = runBlocking {
        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        coEvery {
            auth.getCurrentUser(locator)
        } returns Result.build { throw SpaceNotesError.NetworkUnavailableException }

        logic.event(LoginEvent.OnStart)


        coVerify { auth.getCurrentUser(locator) }
        verify { view.setLoginStatus(ERROR_NETWORK_UNAVAILABLE) }
        verify { view.setStatusDrawable(ANTENNA_EMPTY) }
        verify { view.setAuthButton(RETRY) }
    }

    /**
     *In OnAuthButtonClick, the user wishes to sign in to the application. Instruct View to
     *  create and launch GoogleSignInClient for result, and fire the intent
     * a. User is currently signed out
     * b. User is currently signed in
     * c. network is unavailable
     *
     * a.
     * 1. Check network status: available
     * 2. User result: null
     * 3. start sign in flow
     */
    @Test
    fun `On Auth Button Click signed out`() = runBlocking {

        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        coEvery {
            auth.getCurrentUser(locator)
        } returns Result.build { null }

        logic.event(LoginEvent.OnAuthButtonClick)


        coVerify { auth.getCurrentUser(locator) }
        verify { view.startSignInFlow() }

    }

    /**
     * b.
     * 1. Check network status: available
     * 2. User result: User
     * 3. tell auth to sign user out
     * 4. render user signed out view
     */
    @Test
    fun `On Auth Button Click signed in`() = runBlocking {

        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        coEvery {
            auth.getCurrentUser(locator)
        } returns Result.build { getUser() }

        coEvery {
            auth.signOutCurrentUser(locator)
        } returns Result.build { Unit }


        logic.event(LoginEvent.OnAuthButtonClick)


        coVerify { auth.getCurrentUser(locator) }
        coVerify { auth.signOutCurrentUser(locator) }
        verify { view.setLoginStatus(SIGNED_OUT) }
        verify { view.setStatusDrawable(ANTENNA_FULL) }
        verify { view.setAuthButton(SIGN_IN) }
    }

    /**
     * c.
     * 1. Check network status: unavailable
     * 2. render error view
     */
    @Test
    fun `On Auth Button Click network unavailable`() = runBlocking {
        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        coEvery {
            auth.getCurrentUser(locator)
        } returns Result.build { throw SpaceNotesError.NetworkUnavailableException }



        logic.event(LoginEvent.OnAuthButtonClick)


        coVerify { auth.getCurrentUser(locator) }
        verify { view.setLoginStatus(ERROR_NETWORK_UNAVAILABLE) }
        verify { view.setStatusDrawable(ANTENNA_EMPTY) }
        verify { view.setAuthButton(RETRY) }
    }


    @Test
    fun `On Back Click`() = runBlocking {
        logic.event(LoginEvent.OnBackClick)

        verify { view.startListFeature() }
    }

    /**
     * When the user wishes to create Sign In or create a new account, the result of this
     * action will pop up in onActivityResult(), which is called prior to onResume(). Since
     * we're already preferring pragmatism over separation of concerns in this feature due to tight
     * coupling with Activities, I've chosen to attempt to retrieve the user account from in the
     * activity. After that, it's up to the Logic class and backend to figure things out.
     *
     * a. GoogleSignInAccount succesfully retrieved
     * b. GoogleSignInAcccount was null, or the task threw an exception
     *
     * 1. Pass LoginResult to Logic:
     * 2. Check request code. If RC_SIGN_IN, we know that the result has to do with
     * Signing In.
     * 3.
     *
     */
    @Test
    fun `On Sign In Result RC_SIGN_IN account idToken acquired`() = runBlocking {

        val loginResult = LoginResult(RC_SIGN_IN, testAccount)

        every {
            testAccount.idToken
        } returns testIdToken

        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        coEvery {
            auth.getCurrentUser(locator)
        } returns Result.build { getUser() }

        coEvery {
            auth.createGoogleUser(testIdToken, locator)
        } returns Result.build { true }


        logic.event(LoginEvent.OnGoogleSignInResult(loginResult))

        coVerify { auth.createGoogleUser(testIdToken, locator) }
        verify { view.setLoginStatus(SIGNED_IN) }
        verify { view.showLoopAnimation() }
        verify { view.setAuthButton(SIGN_OUT) }
    }

    /**
     * b.
     */
    @Test
    fun `On Sign In Result RC_SIGN_IN account null`() = runBlocking {

        val loginResult = LoginResult(RC_SIGN_IN, null)

        every {
            dispatcher.provideUIContext()
        } returns Dispatchers.Unconfined

        logic.event(LoginEvent.OnGoogleSignInResult(loginResult))

        verify { view.setLoginStatus(ERROR_AUTH) }
        verify { view.setStatusDrawable(ANTENNA_EMPTY) }
        verify { view.setAuthButton(RETRY) }
    }

}